package pe.axiz.reflectionpoc.infrastructure.plugin;

import pe.axiz.reflectionpoc.domain.annotation.PaymentPlugin;
import pe.axiz.reflectionpoc.domain.annotation.ReflectiveOperation;
import pe.axiz.reflectionpoc.domain.model.*;
import pe.axiz.reflectionpoc.domain.port.PaymentProcessor;
import pe.axiz.reflectionpoc.domain.service.FraudPolicy;

import java.time.Instant;
import java.util.Map;

@PaymentPlugin(code = "CARD", description = "Procesador simulado de tarjetas", priority = 10)
public final class CardPaymentProcessor implements PaymentProcessor<CardPayment> {
    private final FraudPolicy fraudPolicy;

    public CardPaymentProcessor(FraudPolicy fraudPolicy) {
        this.fraudPolicy = fraudPolicy;
    }

    @Override
    @ReflectiveOperation("Valida si el plugin acepta el instrumento")
    public boolean supports(PaymentInstrument instrument) {
        return instrument instanceof CardPayment;
    }

    @Override
    @ReflectiveOperation("Procesa el pago mediante invocación reflectiva")
    public PaymentResult process(PaymentCommand command, CardPayment instrument) {
        var approved = fraudPolicy.approve(command) && instrument.pan() != null && instrument.pan().length() >= 12;
        return new PaymentResult(command.paymentId(), "CARD", approved ? PaymentStatus.APPROVED : PaymentStatus.REJECTED,
                Instant.now(), Map.of("holder", instrument.cardHolder(), "last4", last4(instrument.pan())));
    }

    private String last4(String pan) {
        return pan == null || pan.length() < 4 ? "****" : pan.substring(pan.length() - 4);
    }
}
