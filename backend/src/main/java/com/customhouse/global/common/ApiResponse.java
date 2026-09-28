package com.customhouse.global.common;

/**
 * [담당: 허겸] 공통 인프라 - 전역 응답 규격(DTO)
 * CLAUDE.md 컨벤션: 모든 API가 {success, code, message, data} 형태로 응답하도록 통일한다.
 * code는 성공 시 "OK", 실패 시 ErrorCode(추후 상세 체계 확정 예정, 현재는 CustomException의 code를 그대로 사용)를 담는다.
 */
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "OK", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, "OK", message, data);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }

    public static <T> ApiResponse<T> fail(String code, String message, T data) {
        return new ApiResponse<>(false, code, message, data);
    }
}
