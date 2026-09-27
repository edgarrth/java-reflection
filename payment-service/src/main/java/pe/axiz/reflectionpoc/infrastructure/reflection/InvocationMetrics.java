package pe.axiz.reflectionpoc.infrastructure.reflection;

import pe.axiz.reflectionpoc.domain.model.PaymentStatus;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Cumulative per provider, including retired generations. Each sample is internally consistent. */
public final class InvocationMetrics {
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public void record(String code, PaymentStatus status, long nanos) {
        counters.computeIfAbsent(code, ignored -> new Counter()).record(status, nanos);
    }

    public Map<String, Sample> snapshot() {
        var result = new TreeMap<String, Sample>();
        counters.forEach((code, counter) -> result.put(code, counter.sample()));
        return Map.copyOf(result);
    }

    public record Sample(long completed, long approved, long rejected, long failed, long totalNanos) { }

    private static final class Counter {
        private long completed, approved, rejected, failed, totalNanos;
        synchronized void record(PaymentStatus status, long nanos) {
            completed++;
            totalNanos += nanos;
            if (status == PaymentStatus.APPROVED) approved++;
            else if (status == PaymentStatus.REJECTED) rejected++;
            else failed++;
        }
        synchronized Sample sample() { return new Sample(completed, approved, rejected, failed, totalNanos); }
    }
}
