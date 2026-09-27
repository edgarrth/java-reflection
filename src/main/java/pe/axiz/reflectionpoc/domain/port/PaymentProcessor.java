package pe.axiz.reflectionpoc.domain.port;

import pe.axiz.reflectionpoc.domain.model.PaymentCommand;
import pe.axiz.reflectionpoc.domain.model.PaymentInstrument;
import pe.axiz.reflectionpoc.domain.model.PaymentResult;

public interface PaymentProcessor<T extends PaymentInstrument> {
    boolean supports(PaymentInstrument instrument);
    PaymentResult process(PaymentCommand command, T instrument);
}
