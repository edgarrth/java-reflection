package pe.axiz.reflectionpoc.domain.model;

import pe.axiz.reflectionpoc.domain.annotation.Sensitive;

public record BankTransferPayment(
        String bank,
        @Sensitive String accountNumber
) implements PaymentInstrument {
    @Override
    public String type() {
        return "BANK_TRANSFER";
    }
}
