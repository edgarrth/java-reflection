package pe.axiz.reflectionpoc.infrastructure.web;

import org.springframework.stereotype.Component;
import pe.axiz.reflectionpoc.domain.model.*;

@Component
public class PaymentMapper {
    public PaymentCommand toCommand(PaymentRequest request) {
        var instrument = switch (request.instrumentType()) {
            case "CARD" -> new CardPayment(request.pan(), request.cardHolder(), value(request.expiryMonth()), value(request.expiryYear()), request.cvv());
            case "WALLET" -> new WalletPayment(request.walletProvider(), request.walletToken());
            case "BANK_TRANSFER" -> new BankTransferPayment(request.bank(), request.accountNumber());
            default -> throw new IllegalArgumentException("instrumentType no soportado: " + request.instrumentType());
        };
        return new PaymentCommand(request.paymentId(), request.amount(), request.currency(), instrument);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
