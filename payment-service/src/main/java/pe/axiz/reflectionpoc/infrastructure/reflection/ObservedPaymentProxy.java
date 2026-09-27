package pe.axiz.reflectionpoc.infrastructure.reflection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.axiz.reflectionpoc.domain.model.*;
import pe.axiz.reflectionpoc.domain.port.PaymentProcessor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class ObservedPaymentProxy {
    private static final Logger LOG = LoggerFactory.getLogger(ObservedPaymentProxy.class);

    private ObservedPaymentProxy() { }

    static PaymentProcessor<?> wrap(String code, PaymentProcessor<?> target, InvocationMetrics metrics) {
        return (PaymentProcessor<?>) Proxy.newProxyInstance(PaymentProcessor.class.getClassLoader(),
                new Class<?>[]{PaymentProcessor.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("process")) return invoke(target, method, args);
                    var command = (PaymentCommand) args[0];
                    // Never log instruments, return details, exception messages or stack traces.
                    String paymentId = safeId(command.paymentId());
                    LOG.info("payment.start provider={} paymentId={}", code, paymentId);
                    long start = System.nanoTime();
                    PaymentStatus status = null;
                    try {
                        var result = (PaymentResult) invoke(target, method, args);
                        if (result == null || result.status() == null || !code.equals(result.processor())
                                || !command.paymentId().equals(result.paymentId())) {
                            throw new IllegalStateException("Respuesta incompatible con el contrato");
                        }
                        status = result.status();
                        return result;
                    } finally {
                        long elapsed = System.nanoTime() - start;
                        metrics.record(code, status, elapsed);
                        LOG.info("payment.end provider={} paymentId={} outcome={} durationNanos={}",
                                code, paymentId, status == null ? "ERROR" : status, elapsed);
                    }
                });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            if (cause instanceof RuntimeException || cause instanceof Error) throw cause;
            throw new PluginExecutionException(cause);
        }
    }

    private static String safeId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,64}") ? id : "invalid-id";
    }
}
