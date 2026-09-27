package pe.axiz.reflectionpoc.infrastructure.plugin;

import pe.axiz.reflectionpoc.domain.annotation.PaymentPlugin;
import pe.axiz.reflectionpoc.domain.annotation.ReflectiveOperation;
import pe.axiz.reflectionpoc.domain.model.*;
import pe.axiz.reflectionpoc.domain.port.PaymentProcessor;
import pe.axiz.reflectionpoc.domain.service.FraudPolicy;

import java.time.Instant;
import java.util.Map;

@PaymentPlugin(code = "BANK_TRANSFER", description = "Procesador simulado de transferencias", priority = 30)
public final class BankTransferPaymentProcessor implements PaymentProcessor<BankTransferPayment> {
    private final FraudPolicy fraudPolicy;

    public BankTransferPaymentProcessor(FraudPolicy fraudPolicy) {
        this.fraudPolicy = fraudPolicy;
    }

    @Override
    @ReflectiveOperation("Valida si el plugin acepta el instrumento")
    public boolean supports(PaymentInstrument instrument) {
        return instrument instanceof BankTransferPayment;
    }

    @Override
    @ReflectiveOperation("Procesa el pago mediante invocación reflectiva")
    public PaymentResult process(PaymentCommand command, BankTransferPayment instrument) {
        var approved = fraudPolicy.approve(command) && instrument.accountNumber() != null && !instrument.accountNumber().isBlank();
        return new PaymentResult(command.paymentId(), "BANK_TRANSFER", approved ? PaymentStatus.APPROVED : PaymentStatus.REJECTED,
                Instant.now(), Map.of("bank", instrument.bank()));
    }
}
