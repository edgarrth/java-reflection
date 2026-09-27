package pe.axiz.reflectionpoc.infrastructure;

import org.junit.jupiter.api.Test;
import pe.axiz.reflectionpoc.domain.model.CardPayment;
import pe.axiz.reflectionpoc.domain.port.PaymentProcessor;
import pe.axiz.reflectionpoc.infrastructure.plugin.CardPaymentProcessor;

import java.lang.reflect.ParameterizedType;

import static org.assertj.core.api.Assertions.assertThat;

class PluginGenericsTest {
    @Test
    void debeConservarElTipoGenericoEnRuntime() {
        var generic = CardPaymentProcessor.class.getGenericInterfaces()[0];
        assertThat(generic).isInstanceOf(ParameterizedType.class);
        var parameterized = (ParameterizedType) generic;
        assertThat(parameterized.getRawType()).isEqualTo(PaymentProcessor.class);
        assertThat(parameterized.getActualTypeArguments()[0]).isEqualTo(CardPayment.class);
    }
}
