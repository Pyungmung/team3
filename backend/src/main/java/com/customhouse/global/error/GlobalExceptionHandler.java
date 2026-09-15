package com.customhouse.global.error;

import com.customhouse.global.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [담당: 허겸] 공통 인프라 - 백엔드 공통 예외 처리(Global Exception Handler).
 * 모든 컨트롤러/서비스에서 발생하는 예외를 ApiResponse{success, code, message, data} 규격으로 통일해서 내려준다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 각 도메인 서비스가 의도적으로 던진 비즈니스 예외 (throw new CustomException(ErrorCode.XXX)) */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.name(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR.name(), ErrorCode.VALIDATION_ERROR.getDefaultMessage(), fieldErrors));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiEngineFailure(RestClientException ex) {
        return ResponseEntity.status(ErrorCode.AI_ENGINE_ERROR.getStatus())
                .body(ApiResponse.fail(ErrorCode.AI_ENGINE_ERROR.name(), ErrorCode.AI_ENGINE_ERROR.getDefaultMessage() + " (" + ex.getMessage() + ")"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.getDefaultMessage() + " (" + ex.getMessage() + ")"));
    }
}
