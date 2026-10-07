package com.customhouse.domain.recommendation.service;

// [담당: 송귀성] AI 엔진이 "입력이 잘못됐다"(400/422)고 돌려준 응답에서 사용자에게 보여줄 문구를 뽑는다 (2026-10-07).
// 이전에는 이런 응답이 전부 502(AI_ENGINE_ERROR)로 포장돼 "서버 오류"처럼 보였고 문구에도 원문 JSON이 섞였다.

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class AiEngineErrorMessage {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final String FALLBACK = "입력값이 올바르지 않습니다.";

    /** 422(FastAPI 필드 검증)의 loc에 오는 요청 필드명 -> 화면에서 쓰는 이름 */
    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("annualIncome", "연소득"), Map.entry("coupleAnnualIncome", "부부합산 연소득"),
            Map.entry("deposit", "보유 보증금"), Map.entry("desiredDeposit", "최대 매물 보증금"),
            Map.entry("desiredRent", "최대 매물 월세"), Map.entry("workLocation", "직장 위치"),
            Map.entry("maxCommuteMinutes", "희망 통근시간"), Map.entry("age", "나이"),
            Map.entry("assets", "자산"), Map.entry("militaryServiceMonths", "병역이행기간"),
            Map.entry("newbornAdditionalChildCount", "2년 내 출산한 자녀 수"),
            Map.entry("minorChildOver2YearsCount", "2년 초과 미성년 자녀 수"),
            Map.entry("monthlyRent", "월세"), Map.entry("exclusiveArea", "전용면적"),
            Map.entry("addressKeyword", "주소"), Map.entry("propertyType", "매물유형"), Map.entry("leaseType", "거래유형"));

    private AiEngineErrorMessage() {
    }

    /**
     * 본문 예시: {"detail":"지원하지 않는 직장 위치입니다: ..."} (직접 던진 400) /
     * {"detail":[{"loc":["body","age"],"msg":"..."}]} (422 필드 검증). 읽을 수 없으면 일반 문구.
     */
    public static String from(String body) {
        if (body == null || body.isBlank()) {
            return FALLBACK;
        }
        try {
            JsonNode detail = MAPPER.readTree(body).get("detail");
            if (detail == null || detail.isNull()) {
                return FALLBACK;
            }
            if (detail.isTextual()) {
                return detail.asText().isBlank() ? FALLBACK : detail.asText();
            }
            if (detail.isArray()) {
                Set<String> fields = new LinkedHashSet<>();
                for (JsonNode item : detail) {
                    JsonNode loc = item.get("loc");
                    if (loc != null && loc.isArray() && !loc.isEmpty()) {
                        String name = loc.get(loc.size() - 1).asText();
                        if (!"body".equals(name)) {
                            fields.add(FIELD_LABELS.getOrDefault(name, name));
                        }
                    }
                }
                return fields.isEmpty() ? FALLBACK : "입력값을 확인해주세요: " + String.join(", ", fields);
            }
        } catch (Exception ignored) {
            // JSON이 아니면 아래 일반 문구
        }
        return FALLBACK;
    }
}
