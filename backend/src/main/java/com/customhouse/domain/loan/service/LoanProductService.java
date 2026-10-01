package com.customhouse.domain.loan.service;

import com.customhouse.domain.loan.dto.LoanListResponse;
import com.customhouse.domain.loan.dto.LoanPreference;
import com.customhouse.domain.loan.dto.LoanProductRequest;
import com.customhouse.domain.loan.dto.LoanProductResponse;
import com.customhouse.domain.loan.dto.LoanReferenceUrlRequest;
import com.customhouse.domain.loan.entity.LoanPreferenceKey;
import com.customhouse.domain.loan.entity.LoanProduct;
import com.customhouse.domain.loan.entity.LoanReferenceLink;
import com.customhouse.domain.loan.entity.LoanType;
import com.customhouse.domain.loan.repository.LoanProductRepository;
import com.customhouse.domain.loan.repository.LoanReferenceLinkRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [담당: 송귀성] 전세자금대출 조건(관리자 수정 > 전세자금대출) 조회/저장/삭제.
 * 대출 5종(LoanType)은 고정이고 조건만 저장한다. 저장(수정완료)은 upsert, 삭제는 저장된 조건을 지워 "미설정"으로 되돌린다(멱등).
 * 이후 이자 계산식이 이 조건을 읽어 자격 판별과 우대금리 차감에 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanProductService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, LoanPreference>> PREFERENCES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<List<Double>>> RATE_TABLE_TYPE = new TypeReference<>() {
    };
    /** 대출금리표 크기: 부부합산 연소득 4구간(행) x 임차보증금 3구간(열) - loan_matcher.py의 구간 정의와 짝을 맞춰야 한다. */
    private static final int RATE_TABLE_ROWS = 4;
    private static final int RATE_TABLE_COLS = 3;

    private final LoanProductRepository loanProductRepository;
    private final LoanReferenceLinkRepository loanReferenceLinkRepository;

    public LoanListResponse getAll() {
        List<String> types = Arrays.stream(LoanType.values()).map(LoanType::name).toList();
        // getReferenceUrl()이 null인 행(주소를 지운 뒤에도 행 자체는 남는다)이 섞여 있으면 Collectors.toMap이
        // "값은 null일 수 없다"고 예외를 던지므로, null인 것은 애초에 맵에 넣지 않는다(안 넣으면 get()이 null을 준다).
        Map<String, String> referenceUrls = loanReferenceLinkRepository.findAllByLoanTypeIn(types).stream()
                .filter(link -> link.getReferenceUrl() != null)
                .collect(java.util.stream.Collectors.toMap(LoanReferenceLink::getLoanType, LoanReferenceLink::getReferenceUrl));

        List<LoanProductResponse> loans = Arrays.stream(LoanType.values())
                .map(type -> {
                    String url = referenceUrls.get(type.name());
                    return loanProductRepository.findByLoanType(type.name())
                            .map(p -> toResponse(type, p, url))
                            .orElseGet(() -> emptyResponse(type, url));
                })
                .toList();
        List<LoanListResponse.PreferenceInfo> options = Arrays.stream(LoanPreferenceKey.values())
                .map(k -> new LoanListResponse.PreferenceInfo(k.name(), k.getLabel()))
                .toList();
        return new LoanListResponse(options, loans);
    }

    /** 수정완료: 조건을 저장한다 (처음이면 만들고, 있으면 덮어쓴다). */
    @Transactional
    public LoanProductResponse save(String typeCode, LoanProductRequest request) {
        LoanType type = parseType(typeCode);
        validate(request);

        LoanProduct product = loanProductRepository.findByLoanType(type.name()).orElseGet(() -> LoanProduct.of(type));
        product.update(request.minAge(), request.maxAge(), request.maxIncomeSingle(), request.maxIncomeCouple(),
                request.maxAsset(), request.maxListingDeposit(), request.maxExclusiveArea(),
                request.maxLoanRatioPercent(), request.maxLoanAmount(), serialize(request.preferences()),
                serializeRateTable(request.rateTable()));
        LoanProduct saved = loanProductRepository.save(product);
        String url = loanReferenceLinkRepository.findByLoanType(type.name()).map(LoanReferenceLink::getReferenceUrl).orElse(null);
        return toResponse(type, saved, url);
    }

    /**
     * 참고 확인 페이지 주소만 저장한다 (자격 조건과 무관, 관리자가 그 대출을 조사할 때 참고한 링크를 보관만 한다).
     * LoanProduct와 일부러 분리했다 - 이 링크만 저장했다고 그 대출이 "저장됨"(모든 매물에 적용)이 되면 안 된다.
     * 비워서(빈 문자열) 보내면 저장된 주소를 지운다.
     */
    @Transactional
    public LoanProductResponse saveReferenceUrl(String typeCode, LoanReferenceUrlRequest request) {
        LoanType type = parseType(typeCode);
        String url = request.referenceUrl() == null || request.referenceUrl().isBlank() ? null : request.referenceUrl().trim();

        LoanReferenceLink link = loanReferenceLinkRepository.findByLoanType(type.name())
                .orElseGet(() -> LoanReferenceLink.of(type, null));
        link.updateUrl(url);
        loanReferenceLinkRepository.save(link);

        return loanProductRepository.findByLoanType(type.name())
                .map(p -> toResponse(type, p, url))
                .orElseGet(() -> emptyResponse(type, url));
    }

    /** 저장된(saved) 대출만. 리포트 추천이 AI 엔진에 넘겨 매물별 대출 자격을 판별하는 데 쓴다. */
    public List<LoanProductResponse> getSavedLoans() {
        return getAll().loans().stream().filter(LoanProductResponse::saved).toList();
    }

    /** 삭제: 저장된 조건을 지운다 (참고 확인 페이지 주소는 남는다). 저장된 게 없어도 오류 없이 넘어간다. */
    @Transactional
    public void delete(String typeCode) {
        loanProductRepository.deleteByLoanType(parseType(typeCode).name());
    }

    private static LoanType parseType(String code) {
        return LoanType.fromCode(code)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "알 수 없는 대출 종류입니다."));
    }

    private static void validate(LoanProductRequest request) {
        if (request.minAge() != null && request.maxAge() != null && request.minAge() > request.maxAge()) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "최소 나이는 최대 나이보다 클 수 없습니다.");
        }
        if (request.preferences() != null) {
            for (String key : request.preferences().keySet()) {
                if (LoanPreferenceKey.fromCode(key).isEmpty()) {
                    throw new CustomException(ErrorCode.VALIDATION_ERROR, "알 수 없는 우대사항입니다: " + key);
                }
            }
        }
        if (request.rateTable() != null) {
            List<List<Double>> table = request.rateTable();
            if (table.size() != RATE_TABLE_ROWS) {
                throw new CustomException(ErrorCode.VALIDATION_ERROR, "대출금리표는 연소득 구간 4행이어야 합니다.");
            }
            for (List<Double> row : table) {
                if (row == null || row.size() != RATE_TABLE_COLS) {
                    throw new CustomException(ErrorCode.VALIDATION_ERROR, "대출금리표는 임차보증금 구간 3열이어야 합니다.");
                }
                for (Double rate : row) {
                    if (rate == null || rate < 0 || rate > 15) {
                        throw new CustomException(ErrorCode.VALIDATION_ERROR, "대출금리는 0~15% 사이로 입력해주세요.");
                    }
                }
            }
        }
    }

    /** 우대사항 전체를 정해진 순서로 모두 채워 JSON으로 저장한다 (클라이언트가 보낸 다른 값은 저장하지 않는다). */
    private static String serialize(Map<String, LoanPreference> preferences) {
        Map<String, LoanPreference> normalized = normalize(preferences);
        try {
            return JSON.writeValueAsString(normalized);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static Map<String, LoanPreference> normalize(Map<String, LoanPreference> source) {
        Map<String, LoanPreference> result = new LinkedHashMap<>();
        for (LoanPreferenceKey key : LoanPreferenceKey.values()) {
            LoanPreference p = source == null ? null : source.get(key.name());
            result.put(key.name(), p == null
                    ? LoanPreference.EMPTY
                    : new LoanPreference(p.required(), p.discount() == null ? 0.0 : p.discount(),
                            p.overrideMaxListingDeposit(), p.overrideMaxIncomeSingle(),
                            p.overrideMaxIncomeCouple(), p.overrideMaxLoanAmount(), p.overrideMaxLoanRatioPercent(),
                            p.overrideMaxExclusiveArea()));
        }
        return result;
    }

    private static Map<String, LoanPreference> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return normalize(null);
        }
        try {
            return normalize(JSON.readValue(json, PREFERENCES_TYPE));
        } catch (JacksonException e) {
            log.warn("저장된 우대사항 JSON을 읽지 못해 기본값으로 대체합니다: {}", e.getOriginalMessage());
            return normalize(null);
        }
    }

    /** null이면 그대로 null(아직 금리표 없음 - 임시 고정금리 사용), 있으면 JSON으로 직렬화한다. */
    private static String serializeRateTable(List<List<Double>> rateTable) {
        if (rateTable == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(rateTable);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static List<List<Double>> deserializeRateTable(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, RATE_TABLE_TYPE);
        } catch (JacksonException e) {
            log.warn("저장된 대출금리표 JSON을 읽지 못해 임시 고정금리로 대체합니다: {}", e.getOriginalMessage());
            return null;
        }
    }

    private static LoanProductResponse toResponse(LoanType type, LoanProduct p, String referenceUrl) {
        return new LoanProductResponse(type.name(), type.getLabel(), type.getLeaseType(), true, p.getMinAge(), p.getMaxAge(),
                p.getMaxIncomeSingle(), p.getMaxIncomeCouple(), p.getMaxAsset(), p.getMaxListingDeposit(), p.getMaxExclusiveArea(),
                p.getMaxLoanRatioPercent(), p.getMaxLoanAmount(), deserialize(p.getPreferences()), deserializeRateTable(p.getRateTable()),
                p.getUpdatedAt(), referenceUrl);
    }

    private static LoanProductResponse emptyResponse(LoanType type, String referenceUrl) {
        return new LoanProductResponse(type.name(), type.getLabel(), type.getLeaseType(), false, null, null, null, null, null, null, null,
                null, null, normalize(null), null, null, referenceUrl);
    }
}
