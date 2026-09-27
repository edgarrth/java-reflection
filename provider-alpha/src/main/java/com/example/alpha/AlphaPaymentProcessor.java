package com.example.alpha;

import pe.axiz.reflectionpoc.domain.annotation.PaymentPlugin;
import pe.axiz.reflectionpoc.domain.annotation.ReflectiveOperation;
import pe.axiz.reflectionpoc.domain.model.*;
import pe.axiz.reflectionpoc.domain.port.PaymentProcessor;

import java.time.Instant;
import java.util.Map;

@PaymentPlugin(code = "ALPHA", description = "Proveedor demo multiproducto", priority = 10,
        instruments = {"CARD", "WALLET", "BANK_TRANSFER"}, currencies = {"PEN", "USD"})
public final class AlphaPaymentProcessor implements PaymentProcessor<PaymentInstrument> {
    public AlphaPaymentProcessor() { }

    @Override
    public boolean supports(PaymentInstrument instrument) {
        return instrument != null;
    }

    @Override
    @ReflectiveOperation("Autoriza un pago simulado en ALPHA")
    public PaymentResult process(PaymentCommand command, PaymentInstrument instrument) {
        var details = switch (instrument) {
            case CardPayment card -> Map.<String, Object>of("last4", card.pan().substring(card.pan().length() - 4));
            case WalletPayment wallet -> Map.<String, Object>of("channel", "WALLET");
            case BankTransferPayment bank -> Map.<String, Object>of("channel", "BANK_TRANSFER");
        };
        return new PaymentResult(command.paymentId(), "ALPHA", PaymentStatus.APPROVED, Instant.now(), details);
    }
}
