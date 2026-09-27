package pe.axiz.reflectionpoc.infrastructure.reflection;

public final class PluginLoadException extends RuntimeException {
    public PluginLoadException(Throwable cause) {
        super("Catálogo rechazado; el registro activo no cambió", cause);
    }
}
