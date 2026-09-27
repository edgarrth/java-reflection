package pe.axiz.reflectionpoc.infrastructure.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.axiz.reflectionpoc.application.ProcessPaymentUseCase;
import pe.axiz.reflectionpoc.domain.model.PaymentResult;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final ProcessPaymentUseCase useCase;
    private final PaymentMapper mapper;

    public PaymentController(ProcessPaymentUseCase useCase, PaymentMapper mapper) {
        this.useCase = useCase;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<PaymentResult> process(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(useCase.process(mapper.toCommand(request)));
    }
}
