package pe.axiz.reflectionpoc.infrastructure.reflection;

public final class PluginExecutionException extends RuntimeException {
    public PluginExecutionException(Throwable cause) {
        super("El proveedor no pudo completar la operación", cause);
    }
}
