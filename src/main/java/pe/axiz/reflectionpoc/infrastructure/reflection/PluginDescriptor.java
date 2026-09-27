package pe.axiz.reflectionpoc.infrastructure.reflection;

import java.util.List;

public record PluginDescriptor(
        String code,
        String description,
        int priority,
        String className,
        String genericInstrumentType,
        List<String> constructors,
        List<String> reflectiveMethods
) {
}
