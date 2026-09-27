package pe.axiz.reflectionpoc.infrastructure.web;

import org.springframework.stereotype.Component;
import pe.axiz.reflectionpoc.domain.model.*;

@Component
public class PaymentMapper {
    public PaymentCommand toCommand(PaymentRequest request) {
        switch (request.instrumentType()) {
            case "CARD" -> {
                require(request.pan() != null && request.pan().matches("[0-9]{12,19}"), "pan inválido");
                require(request.cvv() != null && request.cvv().matches("[0-9]{3,4}"), "cvv inválido");
                require(request.cardHolder() != null && !request.cardHolder().isBlank(), "cardHolder requerido");
                require(value(request.expiryMonth()) >= 1 && value(request.expiryMonth()) <= 12, "expiryMonth inválido");
                require(value(request.expiryYear()) >= 2000 && value(request.expiryYear()) <= 9999, "expiryYear inválido");
            }
            case "WALLET" -> {
                require(request.walletProvider() != null && !request.walletProvider().isBlank(), "walletProvider requerido");
                require(request.walletToken() != null && !request.walletToken().isBlank(), "walletToken requerido");
            }
            case "BANK_TRANSFER" -> {
                require(request.bank() != null && !request.bank().isBlank(), "bank requerido");
                require(request.accountNumber() != null && !request.accountNumber().isBlank(), "accountNumber requerido");
            }
            default -> throw new IllegalArgumentException("instrumentType no soportado");
        }
        var instrument = switch (request.instrumentType()) {
            case "CARD" -> new CardPayment(request.pan(), request.cardHolder(), value(request.expiryMonth()), value(request.expiryYear()), request.cvv());
            case "WALLET" -> new WalletPayment(request.walletProvider(), request.walletToken());
            case "BANK_TRANSFER" -> new BankTransferPayment(request.bank(), request.accountNumber());
            default -> throw new IllegalArgumentException("instrumentType no soportado: " + request.instrumentType());
        };
        return new PaymentCommand(request.paymentId(), request.providerCode(), request.amount(), request.currency(), instrument);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
