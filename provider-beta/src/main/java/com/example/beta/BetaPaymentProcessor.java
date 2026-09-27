package com.example.beta;

import pe.axiz.reflectionpoc.domain.annotation.PaymentPlugin;
import pe.axiz.reflectionpoc.domain.annotation.ReflectiveOperation;
import pe.axiz.reflectionpoc.domain.model.*;
import pe.axiz.reflectionpoc.domain.port.PaymentProcessor;

import java.time.Instant;
import java.util.Map;

@PaymentPlugin(code = "BETA", description = "Proveedor externo especializado en tarjetas", priority = 20,
        instruments = {"CARD"}, currencies = {"PEN", "USD"})
public final class BetaPaymentProcessor implements PaymentProcessor<CardPayment> {
    public BetaPaymentProcessor() { }

    @Override
    public boolean supports(PaymentInstrument instrument) {
        return instrument instanceof CardPayment;
    }

    @Override
    @ReflectiveOperation("Autoriza un pago simulado en BETA")
    public PaymentResult process(PaymentCommand command, CardPayment instrument) {
        // Deterministic decline to demonstrate a business rejection, not a technical failure.
        var status = instrument.pan().endsWith("0000") ? PaymentStatus.REJECTED : PaymentStatus.APPROVED;
        return new PaymentResult(command.paymentId(), "BETA", status, Instant.now(),
                Map.of("last4", instrument.pan().substring(instrument.pan().length() - 4), "network", "BETA-DEMO"));
    }
}
