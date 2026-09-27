package pe.axiz.reflectionpoc.domain.model;

import java.time.Instant;
import java.util.Map;

public record PaymentResult(
        String paymentId,
        String processor,
        PaymentStatus status,
        Instant processedAt,
        Map<String, Object> details
) {
}
