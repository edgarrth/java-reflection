package pe.axiz.reflectionpoc.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.axiz.reflectionpoc.application.ProcessPaymentUseCase;
import pe.axiz.reflectionpoc.domain.model.*;
import pe.axiz.reflectionpoc.domain.port.PaymentGateway;
import pe.axiz.reflectionpoc.infrastructure.reflection.PluginExecutionException;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

class PaymentHttpTest {
    private static final String REQUEST = """
            {"paymentId":"PAY-1","providerCode":"ALPHA","amount":100,"currency":"PEN",
             "instrumentType":"CARD","pan":"4111111111111111","cardHolder":"Demo",
             "expiryMonth":12,"expiryYear":2030,"cvv":"123"}
            """;

    private org.springframework.test.web.servlet.MockMvc mvc(PaymentGateway gateway) {
        return MockMvcBuilders.standaloneSetup(new PaymentController(new ProcessPaymentUseCase(gateway), new PaymentMapper()))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void mapsValidRequestAndReturnsPayment() throws Exception {
        mvc(command -> {
            assertThat(command.providerCode()).isEqualTo("ALPHA");
            assertThat(command.instrument()).isInstanceOf(CardPayment.class);
            return new PaymentResult(command.paymentId(), "ALPHA", PaymentStatus.APPROVED, Instant.now(), Map.of());
        }).perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processor").value("ALPHA"));
    }

    @Test
    void invalidPayloadDoesNotEchoSensitiveValues() throws Exception {
        var mvc = mvc(command -> { throw new AssertionError("No debe invocar proveedor"); });
        for (String invalid : new String[]{REQUEST.replace("4111111111111111", "secret-card-value-too-long"),
                REQUEST.replace("\"providerCode\":\"ALPHA\",", ""), REQUEST.replace("\"expiryMonth\":12", "\"expiryMonth\":99")}) {
            String response = mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("4111111111111111", "secret-card-value-too-long", "123");
        }
    }

    @Test
    void distinguishesUnknownProviderFromTechnicalFailure() throws Exception {
        mvc(command -> { throw new NoSuchElementException("Proveedor no registrado"); })
                .perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isNotFound());
        String response = mvc(command -> { throw new PluginExecutionException(new IllegalStateException("secret-provider-error")); })
                .perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isBadGateway()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("secret-provider-error");
    }

    @Test
    void administrationIsDisabledOrRequiresToken() throws Exception {
        for (String token : new String[]{"", "local-test-token"}) {
            var mvc = MockMvcBuilders.standaloneSetup(new AdminProbe()).addFilters(new PluginAdminFilter(token)).build();
            mvc.perform(get("/api/v1/reflection/metrics").servletPath("/api/v1/reflection/metrics"))
                    .andExpect(status().is(token.isEmpty() ? 503 : 401));
            if (!token.isEmpty()) {
                mvc.perform(get("/api/v1/reflection/metrics").servletPath("/api/v1/reflection/metrics")
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
            }
        }
    }

    @org.springframework.web.bind.annotation.RestController
    static class AdminProbe {
        @org.springframework.web.bind.annotation.GetMapping("/api/v1/reflection/metrics")
        Map<String, String> metrics() { return Map.of("status", "ok"); }
    }
}
