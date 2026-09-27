package pe.axiz.reflectionpoc.infrastructure.reflection;

import java.util.List;

public record PluginDescriptor(
        String code,
        String description,
        int priority,
        int apiVersion,
        String jar,
        String sha256,
        List<String> instruments,
        List<String> currencies,
        String className,
        String genericInstrumentType,
        List<String> constructors,
        List<String> reflectiveMethods
) {
}
