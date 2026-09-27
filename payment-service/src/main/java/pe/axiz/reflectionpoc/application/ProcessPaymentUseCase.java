package pe.axiz.reflectionpoc.application;

import org.springframework.stereotype.Service;
import pe.axiz.reflectionpoc.domain.model.PaymentCommand;
import pe.axiz.reflectionpoc.domain.model.PaymentResult;
import pe.axiz.reflectionpoc.domain.model.PaymentStatus;
import pe.axiz.reflectionpoc.domain.port.PaymentGateway;
import pe.axiz.reflectionpoc.domain.service.FraudPolicy;
import java.time.Instant;
import java.util.Map;

@Service
public class ProcessPaymentUseCase {
    private final PaymentGateway gateway;
    private final FraudPolicy fraudPolicy = new FraudPolicy();

    public ProcessPaymentUseCase(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    public PaymentResult process(PaymentCommand command) {
        if (!fraudPolicy.approve(command)) {
            return new PaymentResult(command.paymentId(), command.providerCode(), PaymentStatus.REJECTED,
                    Instant.now(), Map.of("reason", "FRAUD_AMOUNT_LIMIT"));
        }
        return gateway.process(command);
    }
}
