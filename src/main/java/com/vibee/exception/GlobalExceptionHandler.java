package com.vibee.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kế thừa ResponseEntityExceptionHandler để các lỗi chuẩn của Spring MVC (404 sai đường dẫn, 405, 400 do
 * JSON hỏng hoặc enum sai, thiếu tham số...) giữ đúng mã trạng thái thay vì rơi vào handler 500.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    // Cố tình dùng chung 1 thông báo cho "sai email" và "sai mật khẩu" để không lộ email nào tồn tại.
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(LockedException ex) {
        return build(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "This account has been locked");
    }

    // @PreAuthorize ném lỗi này từ trong controller nên phải bắt ở đây.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action");
    }

    // Hai request cùng lúc vi phạm unique constraint: một cái thua ở tầng DB.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT", "The data conflicts with an existing record");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "VALIDATION_FAILED", "Invalid request data", errors));
    }

    /** Mọi lỗi MVC chuẩn còn lại đều đi qua đây để trả cùng một định dạng JSON. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        String code = resolved != null ? resolved.name() : "ERROR";
        String message = body instanceof ProblemDetail pd && pd.getDetail() != null
                ? pd.getDetail()
                : (resolved != null ? resolved.getReasonPhrase() : "Request failed");
        return ResponseEntity.status(statusCode).headers(headers)
                .body(ErrorResponse.of(statusCode.value(), code, message));
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), code, message));
    }
}
