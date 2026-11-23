package com.cocos.challenge.adapter.in.rest;

import com.cocos.challenge.application.exception.ApplicationException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiError> handleApplication(ApplicationException exception,
                                                       HttpServletRequest request) {
        HttpStatus status = switch (exception) {
            case ApplicationException.BadRequest ignored -> HttpStatus.BAD_REQUEST;
            case ApplicationException.NotFound ignored -> HttpStatus.NOT_FOUND;
            case ApplicationException.Conflict ignored -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
                status.getReasonPhrase(), exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleBadRequest(HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
                status.getReasonPhrase(), "Formato de solicitud inválido", request.getRequestURI()));
    }

    public record ApiError(Instant timestamp, int status, String error, String message, String path) {
    }
}
