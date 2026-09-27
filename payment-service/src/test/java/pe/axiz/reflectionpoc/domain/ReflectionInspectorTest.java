package pe.axiz.reflectionpoc.domain;

import org.junit.jupiter.api.Test;
import pe.axiz.reflectionpoc.domain.model.CardPayment;
import pe.axiz.reflectionpoc.domain.model.PaymentInstrument;
import pe.axiz.reflectionpoc.infrastructure.reflection.ReflectionInspector;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectionInspectorTest {
    @Test
    void debeDetectarRecordSensitiveYSealedTypes() {
        var inspector = new ReflectionInspector();
        var card = inspector.inspect(CardPayment.class);
        var instrument = inspector.inspect(PaymentInstrument.class);

        assertThat(card.record()).isTrue();
        assertThat(card.recordComponents()).anyMatch(value -> value.contains("pan") && value.contains("SENSITIVE"));
        assertThat(instrument.sealed()).isTrue();
        assertThat(instrument.permittedSubclasses()).hasSize(3);
    }

    @Test
    void debeUsarCacheDeMetadatos() {
        var inspector = new ReflectionInspector();
        inspector.inspect(CardPayment.class);
        inspector.inspect(CardPayment.class);
        assertThat(inspector.misses()).isEqualTo(1);
        assertThat(inspector.hits()).isEqualTo(1);
    }
}
