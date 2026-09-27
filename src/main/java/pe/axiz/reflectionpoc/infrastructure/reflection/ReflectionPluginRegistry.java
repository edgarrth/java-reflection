package pe.axiz.reflectionpoc.infrastructure.reflection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import pe.axiz.reflectionpoc.domain.annotation.PaymentPlugin;
import pe.axiz.reflectionpoc.domain.annotation.ReflectiveOperation;
import pe.axiz.reflectionpoc.domain.model.PaymentInstrument;
import pe.axiz.reflectionpoc.domain.port.PaymentProcessor;
import pe.axiz.reflectionpoc.domain.service.FraudPolicy;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ReflectionPluginRegistry {
    private static final String ALLOWED_PACKAGE_PREFIX = "pe.axiz.reflectionpoc.infrastructure.plugin.";

    private final Resource catalogResource;
    private final JsonMapper jsonMapper;
    private final FraudPolicy fraudPolicy = new FraudPolicy();
    private final ReflectionInspector inspector;
    private final Map<String, RegisteredPlugin> plugins = new ConcurrentHashMap<>();
    private final LongAdder invocations = new LongAdder();

    public ReflectionPluginRegistry(
            @Value("${poc.plugin-catalog:classpath:datasets/payment-plugins.json}") Resource catalogResource,
            JsonMapper jsonMapper,
            ReflectionInspector inspector) {
        this.catalogResource = catalogResource;
        this.jsonMapper = jsonMapper;
        this.inspector = inspector;
        reload();
    }

    public synchronized List<PluginDescriptor> reload() {
        try (var input = catalogResource.getInputStream()) {
            var catalog = jsonMapper.readValue(input, PluginCatalog.class);
            inspector.clear();
            var loaded = new HashMap<String, RegisteredPlugin>();
            for (var className : catalog.plugins()) {
                var plugin = loadPlugin(className);
                if (loaded.putIfAbsent(plugin.descriptor().code(), plugin) != null) {
                    throw new IllegalStateException("Código de plugin duplicado: " + plugin.descriptor().code());
                }
            }
            plugins.clear();
            plugins.putAll(loaded);
            return list();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar el catálogo de plugins", e);
        }
    }

    public List<PluginDescriptor> list() {
        return plugins.values().stream().map(RegisteredPlugin::descriptor)
                .sorted(Comparator.comparingInt(PluginDescriptor::priority)).toList();
    }

    public PluginDescriptor descriptor(String code) {
        return registered(code).descriptor();
    }

    public ClassInspection inspect(String code) {
        return inspector.inspect(registered(code).type());
    }

    public ReflectionMetrics metrics() {
        return new ReflectionMetrics(inspector.hits(), inspector.misses(), invocations.sum(), inspector.cacheSize());
    }

    public Object invokeProcess(String code, Object command, PaymentInstrument instrument) {
        var registered = registered(code);
        try {
            if (!registered.instrumentType().isInstance(instrument)) {
                throw new IllegalArgumentException("El instrumento no corresponde al tipo genérico del plugin " + code);
            }
            var supports = registered.supportsMethod().invoke(registered.proxy(), instrument);
            if (!(supports instanceof Boolean accepted) || !accepted) {
                throw new IllegalArgumentException("El plugin " + code + " no soporta el instrumento recibido");
            }
            invocations.increment();
            return registered.processMethod().invoke(registered.proxy(), command, instrument);
        } catch (InvocationTargetException e) {
            var cause = e.getTargetException();
            throw new IllegalStateException("El plugin lanzó una excepción: " + cause.getMessage(), cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Falló la invocación reflectiva del plugin " + code, e);
        }
    }

    private RegisteredPlugin registered(String code) {
        var plugin = plugins.get(code);
        if (plugin == null) throw new NoSuchElementException("No existe el plugin: " + code);
        return plugin;
    }

    @SuppressWarnings("unchecked")
    private RegisteredPlugin loadPlugin(String className) throws Exception {
        if (!className.startsWith(ALLOWED_PACKAGE_PREFIX)) {
            throw new SecurityException("Clase fuera del paquete permitido: " + className);
        }
        var type = Class.forName(className);
        if (!PaymentProcessor.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("La clase no implementa PaymentProcessor: " + className);
        }
        var annotation = type.getAnnotation(PaymentPlugin.class);
        if (annotation == null) {
            throw new IllegalArgumentException("La clase no tiene @PaymentPlugin: " + className);
        }

        var instrumentType = resolveInstrumentType(type);
        var constructor = resolveConstructor(type);
        var target = constructor.newInstance(fraudPolicy);
        var processorInterface = PaymentProcessor.class;
        var proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{processorInterface},
                (p, method, args) -> method.invoke(target, args));

        var supports = processorInterface.getMethod("supports", PaymentInstrument.class);
        var proxyProcess = processorInterface.getMethod("process", pe.axiz.reflectionpoc.domain.model.PaymentCommand.class, PaymentInstrument.class);

        var descriptor = new PluginDescriptor(
                annotation.code(), annotation.description(), annotation.priority(), className,
                instrumentType.getName(),
                Arrays.stream(type.getDeclaredConstructors()).map(Object::toString).toList(),
                Arrays.stream(type.getDeclaredMethods())
                        .filter(m -> m.isAnnotationPresent(ReflectiveOperation.class))
                        .map(m -> m.getName() + " -> " + m.getAnnotation(ReflectiveOperation.class).value())
                        .sorted().toList()
        );
        return new RegisteredPlugin(type, instrumentType, proxy, supports, proxyProcess, descriptor);
    }

    private Constructor<?> resolveConstructor(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .filter(c -> Arrays.equals(c.getParameterTypes(), new Class<?>[]{FraudPolicy.class}))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El plugin debe exponer constructor(FraudPolicy): " + type.getName()));
    }

    private Class<? extends PaymentInstrument> resolveInstrumentType(Class<?> type) {
        for (var generic : type.getGenericInterfaces()) {
            if (generic instanceof ParameterizedType parameterized
                    && parameterized.getRawType().equals(PaymentProcessor.class)
                    && parameterized.getActualTypeArguments()[0] instanceof Class<?> argument
                    && PaymentInstrument.class.isAssignableFrom(argument)) {
                return argument.asSubclass(PaymentInstrument.class);
            }
        }
        throw new IllegalArgumentException("No se pudo resolver el tipo genérico del instrumento para " + type.getName());
    }

    public record PluginCatalog(List<String> plugins) { }

    private record RegisteredPlugin(
            Class<?> type,
            Class<? extends PaymentInstrument> instrumentType,
            Object proxy,
            Method supportsMethod,
            Method processMethod,
            PluginDescriptor descriptor) { }
}
