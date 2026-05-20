package com.example.settlement.api.common.exception;

import com.example.settlement.core.domain.exception.PolicyConflictException;
import com.example.settlement.core.domain.exception.PolicyNotFoundException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PolicyConflictException.class)
    ProblemDetail handlePolicyConflict(PolicyConflictException exception) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        detail.setTitle("수수료 정책 기간 중복");
        detail.setType(URI.create("https://settlement.example.com/errors/policy-overlap"));
        return detail;
    }

    @ExceptionHandler(PolicyNotFoundException.class)
    ProblemDetail handlePolicyNotFound(PolicyNotFoundException exception) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        detail.setTitle("수수료 정책 없음");
        detail.setType(URI.create("https://settlement.example.com/errors/policy-not-found"));
        return detail;
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ProblemDetail handleBadRequest(Exception exception) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setTitle("잘못된 요청");
        detail.setType(URI.create("https://settlement.example.com/errors/bad-request"));
        return detail;
    }

    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    ProblemDetail handleRequestBindingError(Exception exception) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setTitle("잘못된 요청");
        detail.setType(URI.create("https://settlement.example.com/errors/bad-request"));
        return detail;
    }
}
