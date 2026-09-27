package pe.axiz.reflectionpoc.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.axiz.reflectionpoc.domain.model.*;
import pe.axiz.reflectionpoc.infrastructure.reflection.*;
import pe.axiz.reflectionpoc.infrastructure.reflection.ReflectionPluginRegistry.CatalogEntry;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static pe.axiz.reflectionpoc.infrastructure.ExternalJarFixture.*;

class ExternalPluginRegistryTest {
    @TempDir Path directory;
    private final JsonMapper json = JsonMapper.builder().build();

    private void catalog(CatalogEntry... entries) throws Exception {
        Files.writeString(directory.resolve("catalog.json"), json.writeValueAsString(new ReflectionPluginRegistry.PluginCatalog(List.of(entries))));
    }

    private ReflectionPluginRegistry registry() {
        return new ReflectionPluginRegistry(directory.resolve("catalog.json").toString(), directory.toString(), json, new ReflectionInspector());
    }

    static PaymentCommand command(String provider) {
        return new PaymentCommand("PAY-1", provider, new BigDecimal("100.00"), "PEN",
                new CardPayment("4111111111111111", "Demo", 12, 2030, "123"));
    }

    @Test
    void loadsClassAbsentFromHostAndAddsAnotherJarWithoutRestart() throws Exception {
        var alpha = build(directory, "Alpha", source("Alpha", "ALPHA", approved("ALPHA")));
        catalog(alpha);
        assertThatThrownBy(() -> Class.forName("external.Alpha")).isInstanceOf(ClassNotFoundException.class);
        try (var registry = registry()) {
            assertThat(registry.process(command("ALPHA")).status()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(registry.descriptor("ALPHA").genericInstrumentType()).isEqualTo(CardPayment.class.getName());
            registry.inspect("ALPHA");
            registry.inspect("ALPHA");
            assertThat(registry.metrics().metadataCacheHits()).isEqualTo(1);
            assertThat(registry.metrics().cachedClasses()).isEqualTo(1);
            var beta = build(directory, "Beta", source("Beta", "BETA", approved("BETA")));
            catalog(alpha, beta);
            registry.reload();
            assertThat(registry.list()).extracting(PluginDescriptor::code).containsExactly("ALPHA", "BETA");
            assertThat(registry.metrics().generation()).isEqualTo(2);
            assertThat(registry.metrics().cachedClasses()).isZero();
            assertThat(registry.process(command("BETA")).processor()).isEqualTo("BETA");
            // Loading uses private copies: the installed source JAR is not locked on Windows.
            Files.move(directory.resolve(alpha.jar()), directory.resolve("renamed.jar"));
            assertThat(registry.process(command("ALPHA")).status()).isEqualTo(PaymentStatus.APPROVED);
        }
    }

    @Test
    void failedReloadPreservesRegistryAndInspectionCache() throws Exception {
        var alpha = build(directory, "Alpha", source("Alpha", "ALPHA", approved("ALPHA")));
        catalog(alpha);
        try (var registry = registry()) {
            registry.inspect("ALPHA");
            catalog(alpha, alpha);
            assertThatThrownBy(registry::reload).isInstanceOf(PluginLoadException.class)
                    .hasRootCauseMessage("Código de proveedor duplicado");
            assertThat(registry.metrics().generation()).isEqualTo(1);
            assertThat(registry.metrics().cachedClasses()).isEqualTo(1);
            assertThat(registry.process(command("ALPHA")).processor()).isEqualTo("ALPHA");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"annotation", "constructor", "generic", "api", "capabilities", "abstract", "contract"})
    void rejectsIncompatiblePluginsBeforePublication(String defect) throws Exception {
        String source = source("Broken", "BROKEN", approved("BROKEN"));
        source = switch (defect) {
            case "annotation" -> source.replaceFirst("@PaymentPlugin[^\\n]+", "");
            case "constructor" -> source.replace("public Broken()", "private Broken()");
            case "generic" -> source.replace("PaymentProcessor<CardPayment>", "PaymentProcessor")
                    .replace("PaymentCommand command, CardPayment instrument", "PaymentCommand command, PaymentInstrument instrument");
            case "api" -> source.replace("description=", "apiVersion=2, description=");
            case "capabilities" -> source.replace("instruments={\"CARD\"}", "instruments={\"WALLET\"}");
            case "abstract" -> source.replace("public class Broken", "public abstract class Broken");
            case "contract" -> source.replace("implements PaymentProcessor<CardPayment>", "");
            default -> throw new AssertionError();
        };
        catalog(build(directory, "Broken", source));
        assertThatThrownBy(this::registry).isInstanceOf(PluginLoadException.class);
    }

    @Test
    void rejectsHashMismatchAndPathTraversal() throws Exception {
        var entry = build(directory, "Alpha", source("Alpha", "ALPHA", approved("ALPHA")));
        catalog(new CatalogEntry(entry.jar(), entry.className(), "0".repeat(64)));
        assertThatThrownBy(this::registry).hasRootCauseMessage("SHA-256 no coincide");
        catalog(new CatalogEntry("../Alpha.jar", entry.className(), entry.sha256()));
        assertThatThrownBy(this::registry).hasRootCauseMessage("Entrada de catálogo inválida");
    }

    @Test
    void validatesInstrumentAndCurrencyBeforeCallingProvider() throws Exception {
        catalog(build(directory, "Alpha", source("Alpha", "ALPHA", approved("ALPHA"))));
        try (var registry = registry()) {
            var card = command("ALPHA");
            var wallet = new PaymentCommand("PAY-2", "ALPHA", card.amount(), "PEN", new WalletPayment("Demo", "token"));
            assertThatThrownBy(() -> registry.process(wallet)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> registry.process(new PaymentCommand("PAY-3", "ALPHA", card.amount(), "EUR", card.instrument())))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(registry.metrics().providers()).isEmpty();
        }
    }

    @Test
    void preservesOriginalExceptionAndCountsTechnicalFailure() throws Exception {
        catalog(build(directory, "Fails", source("Fails", "FAILS", "throw new IllegalArgumentException(\"secret-provider-message\");")));
        try (var registry = registry()) {
            assertThatThrownBy(() -> registry.process(command("FAILS")))
                    .isInstanceOf(PluginExecutionException.class)
                    .hasCauseExactlyInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("secret-provider-message")
                    .hasMessageNotContaining("secret-provider-message");
            var sample = registry.metrics().providers().get("FAILS");
            assertThat(sample.failed()).isEqualTo(1);
            assertThat(sample.completed()).isEqualTo(1);
            assertThat(sample.totalNanos()).isPositive();
        }
    }

    @Test
    void waitsForInflightPaymentBeforeReplacingGeneration() throws Exception {
        Gate.entered = new CountDownLatch(1);
        Gate.release = new CountDownLatch(1);
        Gate.staged = new CountDownLatch(1);
        var old = build(directory, "Old", source("Old", "ALPHA",
                "pe.axiz.reflectionpoc.infrastructure.ExternalJarFixture.Gate.await(); " + approved("ALPHA")));
        var nextSource = source("Next", "BETA", approved("BETA"))
                .replace("public Next() { }", "public Next() { pe.axiz.reflectionpoc.infrastructure.ExternalJarFixture.Gate.staged.countDown(); }");
        var next = build(directory, "Next", nextSource);
        catalog(old);
        try (var registry = registry(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var payment = executor.submit(() -> registry.process(command("ALPHA")));
            try {
                assertThat(Gate.entered.await(5, TimeUnit.SECONDS)).isTrue();
                catalog(next);
                var reload = executor.submit(registry::reload);
                assertThat(Gate.staged.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> reload.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                Gate.release.countDown();
                assertThat(payment.get(5, TimeUnit.SECONDS).processor()).isEqualTo("ALPHA");
                assertThat(reload.get(5, TimeUnit.SECONDS)).extracting(PluginDescriptor::code).containsExactly("BETA");
                assertThat(registry.process(command("BETA")).processor()).isEqualTo("BETA");
            } finally {
                Gate.release.countDown();
            }
        }
    }
}
