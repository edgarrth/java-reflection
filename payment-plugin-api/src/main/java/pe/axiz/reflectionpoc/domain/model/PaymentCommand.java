package pe.axiz.reflectionpoc.domain.model;

import java.math.BigDecimal;

public record PaymentCommand(
        String paymentId,
        String providerCode,
        BigDecimal amount,
        String currency,
        PaymentInstrument instrument
) {
}
