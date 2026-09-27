package pe.axiz.reflectionpoc.infrastructure;

import pe.axiz.reflectionpoc.domain.port.PaymentProcessor;
import pe.axiz.reflectionpoc.infrastructure.reflection.ReflectionPluginRegistry.CatalogEntry;

import javax.tools.ToolProvider;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class ExternalJarFixture {
    private ExternalJarFixture() { }

    public static String source(String name, String code, String body) {
        return """
                package external;
                import pe.axiz.reflectionpoc.domain.annotation.PaymentPlugin;
                import pe.axiz.reflectionpoc.domain.model.*;
                import pe.axiz.reflectionpoc.domain.port.PaymentProcessor;
                import java.time.Instant;
                import java.util.Map;
                @PaymentPlugin(code="%s", description="Fixture", instruments={"CARD"}, currencies={"PEN"})
                public class %s implements PaymentProcessor<CardPayment> {
                    public %s() { }
                    public boolean supports(PaymentInstrument instrument) { return instrument instanceof CardPayment; }
                    public PaymentResult process(PaymentCommand command, CardPayment instrument) {
                        %s
                    }
                }
                """.formatted(code, name, name, body);
    }

    public static String approved(String code) {
        return "return new PaymentResult(command.paymentId(), \"" + code
                + "\", PaymentStatus.APPROVED, Instant.now(), Map.of());";
    }

    public static CatalogEntry build(Path directory, String name, String source) throws Exception {
        Path work = Files.createTempDirectory(directory, "compile-");
        Path java = work.resolve(name + ".java");
        Path classes = Files.createDirectory(work.resolve("classes"));
        Files.writeString(java, source);
        String api = Path.of(PaymentProcessor.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        String fixtures = Path.of(ExternalJarFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        int status = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "25", "-classpath", api + File.pathSeparator + fixtures, "-d", classes.toString(), java.toString());
        if (status != 0) throw new AssertionError("No se pudo compilar el proveedor externo: " + name);
        Path jar = directory.resolve(name + ".jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar)); var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                out.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
        return new CatalogEntry(jar.getFileName().toString(), "external." + name, sha256(jar));
    }

    public static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    /** Test-only coordination, visible through the parent loader; no timing-dependent sleeps in providers. */
    public static final class Gate {
        public static CountDownLatch entered;
        public static CountDownLatch release;
        public static CountDownLatch staged;

        public static void await() {
            entered.countDown();
            try {
                if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Timeout");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
