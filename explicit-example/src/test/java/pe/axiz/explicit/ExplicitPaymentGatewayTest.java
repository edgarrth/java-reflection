package pe.axiz.explicit;

import org.junit.jupiter.api.Test;
import pe.axiz.reflectionpoc.domain.model.*;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import static org.assertj.core.api.Assertions.*;

class ExplicitPaymentGatewayTest {
    @Test
    void explicitRegistrationExecutesAlphaButCannotDiscoverBeta() {
        var gateway = new ExplicitPaymentGateway();
        var card = new CardPayment("4111111111111111", "Demo", 12, 2030, "123");
        assertThat(gateway.process(new PaymentCommand("PAY-1", "ALPHA", BigDecimal.TEN, "PEN", card)).processor()).isEqualTo("ALPHA");
        assertThatThrownBy(() -> gateway.process(new PaymentCommand("PAY-2", "BETA", BigDecimal.TEN, "PEN", card)))
                .isInstanceOf(NoSuchElementException.class);
    }
}
