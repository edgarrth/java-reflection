package pe.axiz.reflectionpoc.infrastructure.reflection;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import pe.axiz.reflectionpoc.domain.model.*;
import pe.axiz.reflectionpoc.domain.port.PaymentProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ObservedPaymentProxyTest {
    @Test
    @SuppressWarnings("unchecked")
    void observesOutcomesWithoutLoggingCredentialsOrExceptionText() {
        Logger logger = (Logger) LoggerFactory.getLogger(ObservedPaymentProxy.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        var metrics = new InvocationMetrics();
        try {
            var command = new PaymentCommand("PAY-SAFE", "DEMO", BigDecimal.TEN, "PEN",
                    new CardPayment("4111111111111111", "Sensitive Holder", 12, 2030, "987"));
            for (String outcome : new String[]{"APPROVED", "REJECTED", "ERROR"}) {
                var proxy = (PaymentProcessor<CardPayment>) ObservedPaymentProxy.wrap("DEMO", new Demo(outcome), metrics);
                if (outcome.equals("ERROR")) {
                    assertThatThrownBy(() -> proxy.process(command, (CardPayment) command.instrument()))
                            .isExactlyInstanceOf(IllegalArgumentException.class).hasMessage("secret-token");
                } else {
                    assertThat(proxy.process(command, (CardPayment) command.instrument()).status().name()).isEqualTo(outcome);
                }
            }
            var sample = metrics.snapshot().get("DEMO");
            assertThat(sample.completed()).isEqualTo(3);
            assertThat(sample.approved()).isEqualTo(1);
            assertThat(sample.rejected()).isEqualTo(1);
            assertThat(sample.failed()).isEqualTo(1);
            assertThat(sample.totalNanos()).isPositive();
            assertThat(appender.list).hasSize(6).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains("provider=DEMO", "paymentId=PAY-SAFE")
                        .doesNotContain("4111111111111111", "Sensitive Holder", "secret-token", "cvv", "pan=");
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getArgumentArray()).doesNotContain(command, command.instrument());
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    public static final class Demo implements PaymentProcessor<CardPayment> {
        private final String outcome;
        Demo(String outcome) { this.outcome = outcome; }
        public boolean supports(PaymentInstrument instrument) { return true; }
        public PaymentResult process(PaymentCommand command, CardPayment instrument) {
            if (outcome.equals("ERROR")) throw new IllegalArgumentException("secret-token");
            return new PaymentResult(command.paymentId(), "DEMO", PaymentStatus.valueOf(outcome), Instant.now(), Map.of());
        }
    }
}
