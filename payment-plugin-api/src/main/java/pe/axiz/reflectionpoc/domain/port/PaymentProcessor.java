package pe.axiz.reflectionpoc.domain.port;

import pe.axiz.reflectionpoc.domain.model.PaymentCommand;
import pe.axiz.reflectionpoc.domain.model.PaymentInstrument;
import pe.axiz.reflectionpoc.domain.model.PaymentResult;

/**
 * SPI v1: public concrete class, public no-arg constructor and a directly declared
 * concrete generic argument. Instances must be thread-safe, side-effect-free on
 * construction and must not own threads/connections. The host supplies the API.
 */
public interface PaymentProcessor<T extends PaymentInstrument> {
    boolean supports(PaymentInstrument instrument);
    PaymentResult process(PaymentCommand command, T instrument);
}
