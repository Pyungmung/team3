package com.customhouse.global.error;

/**
 * [담당: 허겸] 공통 인프라 - 서비스 전역에서 사용하는 커스텀 예외
 * 각 도메인 서비스 로직에서 비즈니스 예외 발생 시 이 예외를 던지면,
 * GlobalExceptionHandler가 ErrorCode에 맞는 HTTP 상태 + ApiResponse 규격으로 변환해준다.
 *
 * 사용 예: throw new CustomException(ErrorCode.NOT_FOUND, "해당 매물을 찾을 수 없습니다.");
 */
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public CustomException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
