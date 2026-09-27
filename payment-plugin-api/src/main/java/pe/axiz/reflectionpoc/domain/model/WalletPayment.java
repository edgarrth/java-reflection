package pe.axiz.reflectionpoc.domain.model;

import pe.axiz.reflectionpoc.domain.annotation.Sensitive;

public record WalletPayment(
        String provider,
        @Sensitive String token
) implements PaymentInstrument {
    @Override
    public String type() {
        return "WALLET";
    }
}
