package pe.axiz.explicit;

import com.example.alpha.AlphaPaymentProcessor;
import pe.axiz.reflectionpoc.domain.model.*;
import pe.axiz.reflectionpoc.domain.port.PaymentGateway;
import pe.axiz.reflectionpoc.domain.port.PaymentProcessor;

import java.util.Map;
import java.util.NoSuchElementException;

/** A deliberately small, working baseline: adding BETA requires a compile-time dependency and code change. */
public final class ExplicitPaymentGateway implements PaymentGateway {
    private final Map<String, PaymentProcessor<PaymentInstrument>> providers = Map.of("ALPHA", new AlphaPaymentProcessor());

    @Override
    public PaymentResult process(PaymentCommand command) {
        var provider = providers.get(command.providerCode());
        if (provider == null) throw new NoSuchElementException("Proveedor no registrado explícitamente");
        if (!provider.supports(command.instrument())) throw new IllegalArgumentException("Instrumento no soportado");
        return provider.process(command, command.instrument());
    }
}
