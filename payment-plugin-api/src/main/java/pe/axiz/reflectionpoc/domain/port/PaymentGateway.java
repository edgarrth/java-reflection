package pe.axiz.reflectionpoc.domain.port;

import pe.axiz.reflectionpoc.domain.model.PaymentCommand;
import pe.axiz.reflectionpoc.domain.model.PaymentResult;

/** Application port. Discovery and reflection are implementation details. */
public interface PaymentGateway {
    PaymentResult process(PaymentCommand command);
}
