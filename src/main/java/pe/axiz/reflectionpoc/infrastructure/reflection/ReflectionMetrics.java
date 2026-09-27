package pe.axiz.reflectionpoc.infrastructure.reflection;

public record ReflectionMetrics(long metadataCacheHits, long metadataCacheMisses, long reflectiveInvocations, int cachedClasses) {
}
