package pe.axiz.reflectionpoc.infrastructure.reflection;

import org.springframework.stereotype.Component;
import pe.axiz.reflectionpoc.domain.annotation.Sensitive;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ReflectionInspector {
    private final ConcurrentHashMap<Class<?>, ClassInspection> cache = new ConcurrentHashMap<>();
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();

    public ClassInspection inspect(Class<?> type) {
        var cached = cache.get(type);
        if (cached != null) {
            hits.increment();
            return cached;
        }
        misses.increment();
        return cache.computeIfAbsent(type, this::buildInspection);
    }

    public long hits() { return hits.sum(); }
    public long misses() { return misses.sum(); }
    public int cacheSize() { return cache.size(); }
    public void clear() { cache.clear(); }

    private ClassInspection buildInspection(Class<?> type) {
        var permitted = type.isSealed()
                ? Arrays.stream(type.getPermittedSubclasses()).map(Class::getName).sorted().toList()
                : List.<String>of();
        var components = type.isRecord()
                ? Arrays.stream(type.getRecordComponents())
                    .map(c -> c.getName() + ":" + c.getGenericType().getTypeName()
                            + (c.isAnnotationPresent(Sensitive.class) ? " [SENSITIVE]" : ""))
                    .toList()
                : List.<String>of();

        return new ClassInspection(
                type.getName(),
                type.getPackageName(),
                Modifier.toString(type.getModifiers()),
                type.isRecord(),
                type.isSealed(),
                permitted,
                Arrays.stream(type.getInterfaces()).map(Class::getTypeName).sorted().toList(),
                Arrays.stream(type.getAnnotations()).map(a -> a.annotationType().getName()).sorted().toList(),
                Arrays.stream(type.getDeclaredConstructors()).map(Object::toString).sorted().toList(),
                Arrays.stream(type.getDeclaredFields())
                        .map(f -> Modifier.toString(f.getModifiers()) + " " + f.getGenericType().getTypeName() + " " + f.getName()
                                + (f.isAnnotationPresent(Sensitive.class) ? " [SENSITIVE]" : ""))
                        .sorted().toList(),
                Arrays.stream(type.getDeclaredMethods()).map(Object::toString).sorted().toList(),
                components,
                Arrays.stream(type.getGenericInterfaces()).map(java.lang.reflect.Type::getTypeName).sorted().toList()
        );
    }
}
