package pe.axiz.reflectionpoc.domain.model;

import pe.axiz.reflectionpoc.domain.annotation.Sensitive;

public record CardPayment(
        @Sensitive String pan,
        String cardHolder,
        int expiryMonth,
        int expiryYear,
        @Sensitive String cvv
) implements PaymentInstrument {
    @Override
    public String type() {
        return "CARD";
    }
}
