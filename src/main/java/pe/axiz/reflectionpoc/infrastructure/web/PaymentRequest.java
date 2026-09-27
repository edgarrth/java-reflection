package pe.axiz.reflectionpoc.infrastructure.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank String paymentId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String instrumentType,
        String pan,
        String cardHolder,
        Integer expiryMonth,
        Integer expiryYear,
        String cvv,
        String walletProvider,
        String walletToken,
        String bank,
        String accountNumber
) { }
