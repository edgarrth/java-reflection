package pe.axiz.reflectionpoc.domain.model;

import java.math.BigDecimal;

public record PaymentCommand(
        String paymentId,
        BigDecimal amount,
        String currency,
        PaymentInstrument instrument
) {
}
