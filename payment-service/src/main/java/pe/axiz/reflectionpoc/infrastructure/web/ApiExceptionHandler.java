package pe.axiz.reflectionpoc.infrastructure.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import pe.axiz.reflectionpoc.infrastructure.reflection.PluginExecutionException;
import pe.axiz.reflectionpoc.infrastructure.reflection.PluginLoadException;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException exception) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        detail.setTitle("Recurso no encontrado");
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(RuntimeException exception) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setTitle("Solicitud inválida");
        return detail;
    }

    @ExceptionHandler(PluginLoadException.class)
    ProblemDetail reloadFailed(PluginLoadException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(PluginExecutionException.class)
    ProblemDetail providerFailed(PluginExecutionException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ProblemDetail invalidPayload(Exception exception) {
        // Validation exceptions can contain rejected values, including payment credentials.
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Revise los campos de la solicitud");
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail internalFailure(IllegalStateException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo completar la operación");
    }
}
