package pe.axiz.reflectionpoc.application;

import org.springframework.stereotype.Service;
import pe.axiz.reflectionpoc.domain.model.PaymentCommand;
import pe.axiz.reflectionpoc.domain.model.PaymentResult;
import pe.axiz.reflectionpoc.infrastructure.reflection.ReflectionPluginRegistry;

@Service
public class ProcessPaymentUseCase {
    private final ReflectionPluginRegistry registry;

    public ProcessPaymentUseCase(ReflectionPluginRegistry registry) {
        this.registry = registry;
    }

    public PaymentResult process(PaymentCommand command) {
        var result = registry.invokeProcess(command.instrument().type(), command, command.instrument());
        return (PaymentResult) result;
    }
}
