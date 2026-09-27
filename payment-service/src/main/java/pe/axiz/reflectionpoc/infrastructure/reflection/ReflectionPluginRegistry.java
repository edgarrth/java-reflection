package pe.axiz.reflectionpoc.infrastructure.reflection;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.axiz.reflectionpoc.domain.annotation.PaymentPlugin;
import pe.axiz.reflectionpoc.domain.annotation.ReflectiveOperation;
import pe.axiz.reflectionpoc.domain.model.*;
import pe.axiz.reflectionpoc.domain.port.PaymentGateway;
import pe.axiz.reflectionpoc.domain.port.PaymentProcessor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.jar.JarFile;

@Component
public final class ReflectionPluginRegistry implements PaymentGateway, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ReflectionPluginRegistry.class);
    private static final Map<String, Class<? extends PaymentInstrument>> INSTRUMENTS = Map.of(
            "CARD", CardPayment.class, "WALLET", WalletPayment.class, "BANK_TRANSFER", BankTransferPayment.class);
    private final Path catalogPath;
    private final Path pluginDirectory;
    private final JsonMapper jsonMapper;
    private final ReflectionInspector inspector;
    private final InvocationMetrics invocations = new InvocationMetrics();
    // Fair lock: publication waits for in-flight payments; later readers cannot starve it.
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock(true);
    private Snapshot active = new Snapshot(Map.of(), List.of(), 0);
    private boolean closed;

    public ReflectionPluginRegistry(
            @Value("${poc.plugin-catalog}") String catalogPath,
            @Value("${poc.plugin-directory}") String pluginDirectory,
            JsonMapper jsonMapper, ReflectionInspector inspector) {
        this.catalogPath = Path.of(catalogPath).toAbsolutePath().normalize();
        this.pluginDirectory = Path.of(pluginDirectory).toAbsolutePath().normalize();
        this.jsonMapper = jsonMapper;
        this.inspector = inspector;
        reload();
    }

    public synchronized List<PluginDescriptor> reload() {
        if (closed) throw new IllegalStateException("Registro cerrado");
        var resources = new ArrayList<LoadedJar>();
        Snapshot next;
        try (var input = Files.newInputStream(catalogPath)) {
            var catalog = jsonMapper.readValue(input, PluginCatalog.class);
            if (catalog.plugins() == null || catalog.plugins().isEmpty() || catalog.plugins().size() > 32) {
                throw new IllegalArgumentException("El catálogo debe contener entre 1 y 32 proveedores");
            }
            var loaded = new HashMap<String, RegisteredPlugin>();
            for (var entry : catalog.plugins()) {
                var plugin = loadPlugin(entry, resources);
                if (loaded.putIfAbsent(plugin.descriptor().code(), plugin) != null) {
                    throw new IllegalArgumentException("Código de proveedor duplicado");
                }
            }
            next = new Snapshot(Map.copyOf(loaded), List.copyOf(resources), active.generation() + 1);
        } catch (Exception | LinkageError e) {
            resources.forEach(LoadedJar::close);
            // The cause is retained for local diagnosis, never included in the HTTP body or logs.
            LOG.warn("plugin.reload rejected errorType={}", e.getClass().getSimpleName());
            throw new PluginLoadException(e);
        }

        lifecycle.writeLock().lock();
        try {
            var previous = active;
            active = next;
            inspector.clear(); // No reader can reinsert a retired Class while this lock is held.
            previous.close();
            LOG.info("plugin.reload generation={} providers={}", next.generation(), next.plugins().keySet());
            return descriptors(next);
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    public List<PluginDescriptor> list() { return read(() -> descriptors(active)); }
    public PluginDescriptor descriptor(String code) { return read(() -> registered(code).descriptor()); }
    public ClassInspection inspect(String code) { return read(() -> inspector.inspect(registered(code).type())); }
    public ReflectionMetrics metrics() {
        return read(() -> new ReflectionMetrics(inspector.hits(), inspector.misses(), inspector.cacheSize(),
                active.generation(), invocations.snapshot()));
    }

    @Override
    public PaymentResult process(PaymentCommand command) {
        return read(() -> {
            var plugin = registered(command.providerCode());
            var descriptor = plugin.descriptor();
            if (!plugin.instrumentType().isInstance(command.instrument())
                    || !descriptor.instruments().contains(command.instrument().type())) {
                throw new IllegalArgumentException("Instrumento no soportado por el proveedor");
            }
            if (!descriptor.currencies().contains(command.currency())) {
                throw new IllegalArgumentException("Moneda no soportada por el proveedor");
            }
            try {
                if (!plugin.processor().supports(command.instrument())) {
                    throw new IllegalArgumentException("El proveedor rechazó un instrumento declarado como compatible");
                }
                // Normal SPI call; reflection is used inside the observation proxy, not to rediscover methods.
                return invokeTyped(plugin.processor(), command);
            } catch (RuntimeException | LinkageError e) {
                throw e instanceof PluginExecutionException failure ? failure : new PluginExecutionException(e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends PaymentInstrument> PaymentResult invokeTyped(PaymentProcessor<T> processor, PaymentCommand command) {
        // Checked against the generic type discovered at registration before entering this adapter.
        return processor.process(command, (T) command.instrument());
    }

    private RegisteredPlugin registered(String code) {
        var plugin = active.plugins().get(code);
        if (plugin == null) throw new NoSuchElementException("Proveedor no registrado");
        return plugin;
    }

    private static List<PluginDescriptor> descriptors(Snapshot snapshot) {
        return snapshot.plugins().values().stream().map(RegisteredPlugin::descriptor)
                .sorted(Comparator.comparingInt(PluginDescriptor::priority).thenComparing(PluginDescriptor::code)).toList();
    }

    private <T> T read(Supplier<T> action) {
        lifecycle.readLock().lock();
        try {
            if (closed) throw new IllegalStateException("Registro cerrado");
            return action.get();
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    private RegisteredPlugin loadPlugin(CatalogEntry entry, List<LoadedJar> resources) throws Exception {
        if (entry == null || entry.jar() == null || !entry.jar().matches("[A-Za-z0-9][A-Za-z0-9._-]*\\.jar")
                || entry.sha256() == null || !entry.sha256().matches("[a-fA-F0-9]{64}")
                || entry.className() == null || !entry.className().matches("[A-Za-z_$][A-Za-z0-9_$.]*")) {
            throw new IllegalArgumentException("Entrada de catálogo inválida");
        }
        Path root = pluginDirectory.toRealPath();
        Path source = root.resolve(entry.jar()).toRealPath();
        if (!source.startsWith(root) || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("JAR fuera del directorio autorizado");
        }

        // Copy before hashing/loading: uploads remain unlocked, and each generation uses an immutable copy.
        Path copy = Files.createTempFile("payment-plugin-", ".jar");
        var resource = new LoadedJar(copy);
        resources.add(resource);
        Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
        String hash;
        try (var bytes = Files.newInputStream(copy)) {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = bytes.read(buffer)) != -1) digest.update(buffer, 0, count);
            hash = HexFormat.of().formatHex(digest.digest());
        }
        if (!hash.equalsIgnoreCase(entry.sha256())) throw new IllegalArgumentException("SHA-256 no coincide");
        validateJar(copy);
        var loader = new URLClassLoader(new java.net.URL[]{copy.toUri().toURL()}, PaymentProcessor.class.getClassLoader());
        resource.loader = loader;
        var type = Class.forName(entry.className(), false, loader);
        if (type.getClassLoader() != loader || !PaymentProcessor.class.isAssignableFrom(type)
                || !Modifier.isPublic(type.getModifiers()) || Modifier.isAbstract(type.getModifiers()) || type.isInterface()) {
            throw new IllegalArgumentException("El JAR debe definir una implementación pública concreta de PaymentProcessor");
        }
        var metadata = type.getAnnotation(PaymentPlugin.class);
        var instrumentType = resolveInstrumentType(type);
        validateMetadata(metadata, instrumentType);
        // Public API only: no setAccessible and no Spring injection into external code.
        var constructor = type.getConstructor();
        var target = (PaymentProcessor<?>) constructor.newInstance();
        var proxy = ObservedPaymentProxy.wrap(metadata.code(), target, invocations);
        var descriptor = new PluginDescriptor(metadata.code(), metadata.description(), metadata.priority(),
                metadata.apiVersion(), entry.jar(), hash, List.of(metadata.instruments()), List.of(metadata.currencies()),
                type.getName(), instrumentType.getName(),
                Arrays.stream(type.getConstructors()).map(Object::toString).sorted().toList(),
                Arrays.stream(type.getDeclaredMethods()).filter(m -> !m.isBridge() && !m.isSynthetic())
                        .filter(m -> m.isAnnotationPresent(ReflectiveOperation.class))
                        .map(m -> m.getName() + " -> " + m.getAnnotation(ReflectiveOperation.class).value()).sorted().toList());
        return new RegisteredPlugin(type, instrumentType, proxy, descriptor);
    }

    private static void validateJar(Path path) throws IOException {
        try (var jar = new JarFile(path.toFile())) {
            if (jar.getManifest() != null && jar.getManifest().getMainAttributes().getValue("Class-Path") != null) {
                throw new IllegalArgumentException("El JAR no puede declarar dependencias externas en Class-Path");
            }
            if (jar.stream().anyMatch(e -> e.getName().endsWith(".class")
                    && e.getName().contains("pe/axiz/reflectionpoc/"))) {
                throw new IllegalArgumentException("El proveedor no puede empaquetar clases del host ni del contrato");
            }
        }
    }

    private static void validateMetadata(PaymentPlugin metadata, Class<? extends PaymentInstrument> generic) {
        if (metadata == null || metadata.apiVersion() != 1 || !metadata.code().matches("[A-Z][A-Z0-9_]{0,31}")
                || metadata.description().isBlank() || metadata.instruments().length == 0 || metadata.currencies().length == 0) {
            throw new IllegalArgumentException("Metadata o versión de SPI inválida");
        }
        var instruments = new HashSet<String>();
        for (String instrument : metadata.instruments()) {
            var type = INSTRUMENTS.get(instrument);
            if (type == null || !generic.isAssignableFrom(type) || !instruments.add(instrument)) {
                throw new IllegalArgumentException("Capacidades incompatibles con el tipo genérico");
            }
        }
        var currencies = new HashSet<String>();
        for (String currency : metadata.currencies()) {
            if (!currency.matches("[A-Z]{3}") || !currencies.add(currency)) {
                throw new IllegalArgumentException("Moneda inválida o duplicada");
            }
            Currency.getInstance(currency);
        }
    }

    private static Class<? extends PaymentInstrument> resolveInstrumentType(Class<?> type) {
        for (var generic : type.getGenericInterfaces()) {
            if (generic instanceof ParameterizedType parameterized
                    && parameterized.getRawType().equals(PaymentProcessor.class)
                    && parameterized.getActualTypeArguments()[0] instanceof Class<?> argument
                    && PaymentInstrument.class.isAssignableFrom(argument)) {
                return argument.asSubclass(PaymentInstrument.class);
            }
        }
        throw new IllegalArgumentException("Declare directamente PaymentProcessor con un instrumento concreto");
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        lifecycle.writeLock().lock();
        try {
            if (closed) return;
            closed = true;
            active.close();
            active = new Snapshot(Map.of(), List.of(), active.generation());
            inspector.clear();
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    public record PluginCatalog(List<CatalogEntry> plugins) { }
    public record CatalogEntry(String jar, String className, String sha256) { }
    private record RegisteredPlugin(Class<?> type, Class<? extends PaymentInstrument> instrumentType,
                                    PaymentProcessor<?> processor, PluginDescriptor descriptor) { }
    private record Snapshot(Map<String, RegisteredPlugin> plugins, List<LoadedJar> resources, long generation) {
        void close() { resources.forEach(LoadedJar::close); }
    }

    private static final class LoadedJar {
        private final Path copy;
        private URLClassLoader loader;
        LoadedJar(Path copy) { this.copy = copy; }
        void close() {
            try {
                if (loader != null) loader.close();
            } catch (IOException e) {
                LOG.warn("plugin.loader.close failed errorType={}", e.getClass().getSimpleName());
            }
            try {
                Files.deleteIfExists(copy);
            } catch (IOException e) {
                LOG.warn("plugin.copy.delete failed errorType={}", e.getClass().getSimpleName());
            }
        }
    }
}
