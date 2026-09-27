package pe.axiz.reflectionpoc.domain.service;

import pe.axiz.reflectionpoc.domain.model.PaymentCommand;

import java.math.BigDecimal;

public final class FraudPolicy {
    private static final BigDecimal MAX_POC_AMOUNT = new BigDecimal("10000.00");

    public boolean approve(PaymentCommand command) {
        return command.amount() != null
                && command.amount().signum() > 0
                && command.amount().compareTo(MAX_POC_AMOUNT) <= 0;
    }
}
