package pe.axiz.reflectionpoc.domain.model;

public sealed interface PaymentInstrument permits CardPayment, WalletPayment, BankTransferPayment {
    String type();
}
