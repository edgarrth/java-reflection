package pe.axiz.reflectionpoc.infrastructure.reflection;

import java.util.Map;

public record ReflectionMetrics(long metadataCacheHits, long metadataCacheMisses, int cachedClasses,
                                long generation, Map<String, InvocationMetrics.Sample> providers) { }
