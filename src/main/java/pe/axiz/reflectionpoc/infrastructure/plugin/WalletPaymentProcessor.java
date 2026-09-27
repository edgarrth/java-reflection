package pe.axiz.reflectionpoc.infrastructure.plugin;

import pe.axiz.reflectionpoc.domain.annotation.PaymentPlugin;
import pe.axiz.reflectionpoc.domain.annotation.ReflectiveOperation;
import pe.axiz.reflectionpoc.domain.model.*;
import pe.axiz.reflectionpoc.domain.port.PaymentProcessor;
import pe.axiz.reflectionpoc.domain.service.FraudPolicy;

import java.time.Instant;
import java.util.Map;

@PaymentPlugin(code = "WALLET", description = "Procesador simulado de billeteras", priority = 20)
public final class WalletPaymentProcessor implements PaymentProcessor<WalletPayment> {
    private final FraudPolicy fraudPolicy;

    public WalletPaymentProcessor(FraudPolicy fraudPolicy) {
        this.fraudPolicy = fraudPolicy;
    }

    @Override
    @ReflectiveOperation("Valida si el plugin acepta el instrumento")
    public boolean supports(PaymentInstrument instrument) {
        return instrument instanceof WalletPayment;
    }

    @Override
    @ReflectiveOperation("Procesa el pago mediante invocación reflectiva")
    public PaymentResult process(PaymentCommand command, WalletPayment instrument) {
        var approved = fraudPolicy.approve(command) && instrument.token() != null && !instrument.token().isBlank();
        return new PaymentResult(command.paymentId(), "WALLET", approved ? PaymentStatus.APPROVED : PaymentStatus.REJECTED,
                Instant.now(), Map.of("provider", instrument.provider()));
    }
}
