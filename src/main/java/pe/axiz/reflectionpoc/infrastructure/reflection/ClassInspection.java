package pe.axiz.reflectionpoc.infrastructure.reflection;

import java.util.List;

public record ClassInspection(
        String className,
        String packageName,
        String modifiers,
        boolean record,
        boolean sealed,
        List<String> permittedSubclasses,
        List<String> interfaces,
        List<String> annotations,
        List<String> constructors,
        List<String> fields,
        List<String> methods,
        List<String> recordComponents,
        List<String> genericInterfaces
) {
}
