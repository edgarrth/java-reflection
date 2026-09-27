package pe.axiz.reflectionpoc.infrastructure.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String paymentId,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}") String providerCode,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotBlank @Pattern(regexp = "CARD|WALLET|BANK_TRANSFER") String instrumentType,
        @Size(max = 19) String pan,
        @Size(max = 100) String cardHolder,
        Integer expiryMonth,
        Integer expiryYear,
        @Size(max = 4) String cvv,
        @Size(max = 64) String walletProvider,
        @Size(max = 256) String walletToken,
        @Size(max = 64) String bank,
        @Size(max = 64) String accountNumber
) { }
