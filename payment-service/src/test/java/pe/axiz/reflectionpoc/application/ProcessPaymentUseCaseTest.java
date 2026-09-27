package pe.axiz.reflectionpoc.application;

import org.junit.jupiter.api.Test;
import pe.axiz.reflectionpoc.domain.model.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class ProcessPaymentUseCaseTest {
    @Test
    void fraudPolicyPreventsProviderInvocation() {
        var useCase = new ProcessPaymentUseCase(command -> { throw new AssertionError("Proveedor no debe ejecutarse"); });
        var result = useCase.process(new PaymentCommand("PAY-1", "ALPHA", new BigDecimal("10000.01"), "PEN",
                new WalletPayment("Demo", "secret")));
        assertThat(result.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(result.details()).containsEntry("reason", "FRAUD_AMOUNT_LIMIT");
    }

    @Test
    void delegatesAllowedPaymentToPort() {
        var command = new PaymentCommand("PAY-1", "ALPHA", new BigDecimal("10000.00"), "PEN", new WalletPayment("Demo", "secret"));
        var expected = new PaymentResult(command.paymentId(), "ALPHA", PaymentStatus.APPROVED, java.time.Instant.now(), java.util.Map.of());
        var useCase = new ProcessPaymentUseCase(received -> { assertThat(received).isSameAs(command); return expected; });
        assertThat(useCase.process(command)).isSameAs(expected);
    }
}
