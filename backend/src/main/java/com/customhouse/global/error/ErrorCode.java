package com.customhouse.global.error;

import org.springframework.http.HttpStatus;

/**
 * [담당: 허겸] 공통 인프라 - 에러 코드 체계
 * CLAUDE.md TBD 항목("CustomException 에러 코드 체계 상세")의 1차 초안입니다.
 * 각 도메인이 개발되면서 필요한 코드를 추가해 나가면 됩니다 (예: USER_NOT_FOUND, PAYMENT_FAILED 등).
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    AI_ENGINE_ERROR(HttpStatus.BAD_GATEWAY, "AI 추천 엔진(customhouse-ai)과 통신 중 오류가 발생했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // --- 회원/인증 (담당: 허겸) ---
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 유효하지 않습니다."),

    // --- 결제/구독 (담당: 황진구) ---
    PAYMENT_FAILED(HttpStatus.BAD_GATEWAY, "결제 처리 중 오류가 발생했습니다."),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "구독 정보가 없습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
