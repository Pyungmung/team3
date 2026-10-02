package com.customhouse.domain.loan.service;

import com.customhouse.domain.loan.dto.LoanListResponse;
import com.customhouse.domain.loan.dto.LoanPreference;
import com.customhouse.domain.loan.dto.LoanProductRequest;
import com.customhouse.domain.loan.dto.LoanProductResponse;
import com.customhouse.domain.loan.entity.LoanPreferenceKey;
import com.customhouse.domain.loan.entity.LoanProduct;
import com.customhouse.domain.loan.entity.LoanReferenceLink;
import com.customhouse.domain.loan.repository.LoanProductRepository;
import com.customhouse.domain.loan.repository.LoanReferenceLinkRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [담당: 송귀성] 전세자금대출 조건 서비스 테스트. 저장소는 목으로 바꾸고 저장/삭제/검증 규칙을 확인한다.
 */
class LoanProductServiceTest {

    private LoanProductRepository repository;
    private LoanReferenceLinkRepository referenceLinkRepository;
    private LoanProductService service;

    @BeforeEach
    void setUp() {
        repository = mock(LoanProductRepository.class);
        when(repository.save(any(LoanProduct.class))).thenAnswer(inv -> inv.getArgument(0));
        referenceLinkRepository = mock(LoanReferenceLinkRepository.class);
        when(referenceLinkRepository.findAllByLoanTypeIn(any())).thenReturn(List.of());
        when(referenceLinkRepository.save(any(LoanReferenceLink.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new LoanProductService(repository, referenceLinkRepository);
    }

    private LoanProductRequest request(Integer minAge, Integer maxAge, Map<String, LoanPreference> prefs) {
        return new LoanProductRequest(minAge, maxAge, 5000, 6000, 33700, 20000, 85.0, 80.0, 20000,
                null, null, null, null, null, prefs, null);
    }

    @Test
    void 대출_4종은_저장이_없어도_모두_내려오고_우대사항이_전부_채워진다() {
        when(repository.findByLoanType(any())).thenReturn(Optional.empty());

        LoanListResponse res = service.getAll();

        assertThat(res.loans()).hasSize(4).allSatisfy(l -> {
            assertThat(l.saved()).isFalse();
            assertThat(l.preferences()).hasSize(LoanPreferenceKey.values().length);
        });
        assertThat(res.preferenceOptions()).extracting("key")
                .containsExactly("BASIC_LIVELIHOOD", "NEAR_POOR", "SINGLE_PARENT", "INDEPENDENT_YOUTH", "NEWLYWED",
                        "DUAL_INCOME", "ONE_CHILD", "TWO_CHILDREN", "MULTI_CHILD",
                        "DISABLED", "MULTICULTURAL", "ELDERLY_DEPENDENT", "ELDERLY_HOUSEHOLD",
                        "NO_HOME", "SME_EMPLOYED_YOUTH", "AGE_UNDER_25", "AGE_25_OR_OLDER",
                        "NEWBORN_ADDITIONAL_CHILD", "MINOR_CHILD_OVER_2YEARS");
        assertThat(res.loans()).extracting("name").contains("일반 버팀목 전세대출", "청년전용 보증부월세대출");
    }

    @Test
    void 대출마다_대상_매물_유형이_내려오고_저장된_대출만_추천에_넘긴다() {
        LoanProduct saved = LoanProduct.of(com.customhouse.domain.loan.entity.LoanType.YOUTH_MONTHLY_RENT);
        when(repository.findByLoanType(any())).thenReturn(Optional.empty());
        when(repository.findByLoanType("YOUTH_MONTHLY_RENT")).thenReturn(Optional.of(saved));

        assertThat(service.getAll().loans()).extracting("type", "leaseType").contains(
                org.assertj.core.groups.Tuple.tuple("GENERAL_BEOTIMMOK", "전세"),
                org.assertj.core.groups.Tuple.tuple("YOUTH_MONTHLY_RENT", "월세"));
        assertThat(service.getSavedLoans()).extracting("type").containsExactly("YOUTH_MONTHLY_RENT");
    }

    @Test
    void 저장하면_조건과_우대사항이_그대로_돌아오고_빠진_우대사항은_기본값이다() {
        when(repository.findByLoanType("YOUTH_BEOTIMMOK")).thenReturn(Optional.empty());
        Map<String, LoanPreference> prefs = new HashMap<>();
        prefs.put("NEWLYWED", new LoanPreference(true, 0.2, null, null, null, null, null, null));

        LoanProductResponse res = service.save("YOUTH_BEOTIMMOK", request(19, 34, prefs));

        assertThat(res.saved()).isTrue();
        assertThat(res.minAge()).isEqualTo(19);
        assertThat(res.maxAge()).isEqualTo(34);
        assertThat(res.maxAsset()).isEqualTo(33700);
        assertThat(res.maxListingDeposit()).isEqualTo(20000);
        assertThat(res.maxExclusiveArea()).isEqualTo(85.0);
        assertThat(res.maxLoanRatioPercent()).isEqualTo(80.0);
        assertThat(res.maxLoanAmount()).isEqualTo(20000);
        assertThat(res.preferences().get("NEWLYWED")).isEqualTo(new LoanPreference(true, 0.2, null, null, null, null, null, null));
        assertThat(res.preferences().get("NO_HOME")).isEqualTo(LoanPreference.EMPTY);
        assertThat(res.preferences()).hasSize(LoanPreferenceKey.values().length);
        assertThat(res.preferences().get("SME_EMPLOYED_YOUTH")).isEqualTo(LoanPreference.EMPTY);
    }

    @Test
    void 우대사항의_한도_재반영_값도_그대로_저장되고_돌아온다() {
        when(repository.findByLoanType("YOUTH_BEOTIMMOK")).thenReturn(Optional.empty());
        Map<String, LoanPreference> prefs = new HashMap<>();
        prefs.put("NEWLYWED", new LoanPreference(false, 0.0, 30000, 6000, 8000, 25000, 90.0, 70.0));

        LoanProductResponse res = service.save("YOUTH_BEOTIMMOK", request(19, 34, prefs));

        assertThat(res.preferences().get("NEWLYWED")).isEqualTo(new LoanPreference(false, 0.0, 30000, 6000, 8000, 25000, 90.0, 70.0));
        // 재반영 값을 넣지 않은 다른 우대사항은 그대로 EMPTY(전부 null)다
        assertThat(res.preferences().get("MULTI_CHILD")).isEqualTo(LoanPreference.EMPTY);
    }

    @Test
    void 대출금리표를_저장하면_4행_3열_그대로_돌아온다() {
        when(repository.findByLoanType("GENERAL_BEOTIMMOK")).thenReturn(Optional.empty());
        List<List<Double>> rateTable = List.of(
                List.of(2.5, 2.6, 2.7),
                List.of(2.7, 2.8, 2.9),
                List.of(3.0, 3.1, 3.2),
                List.of(3.3, 3.4, 3.5));
        LoanProductRequest req = new LoanProductRequest(19, 34, 5000, 6000, 33700, 20000, 85.0, 80.0, 20000, null, null, null, null, null, Map.of(), rateTable);

        LoanProductResponse res = service.save("GENERAL_BEOTIMMOK", req);

        assertThat(res.rateTable()).isEqualTo(rateTable);
    }

    @Test
    void 대출금리표를_저장하지_않으면_null이다() {
        when(repository.findByLoanType("GENERAL_BEOTIMMOK")).thenReturn(Optional.empty());

        LoanProductResponse res = service.save("GENERAL_BEOTIMMOK", request(19, 34, Map.of()));

        assertThat(res.rateTable()).isNull();
    }

    @Test
    void 대출금리표는_4행이_아니면_검증_오류() {
        List<List<Double>> wrongRows = List.of(List.of(2.5, 2.6, 2.7));
        LoanProductRequest req = new LoanProductRequest(19, 34, 5000, 6000, 33700, 20000, 85.0, 80.0, 20000, null, null, null, null, null, Map.of(), wrongRows);

        assertThatThrownBy(() -> service.save("GENERAL_BEOTIMMOK", req))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void 대출금리표는_한_행이라도_3열이_아니면_검증_오류() {
        List<List<Double>> wrongCols = List.of(
                List.of(2.5, 2.6),
                List.of(2.7, 2.8, 2.9),
                List.of(3.0, 3.1, 3.2),
                List.of(3.3, 3.4, 3.5));
        LoanProductRequest req = new LoanProductRequest(19, 34, 5000, 6000, 33700, 20000, 85.0, 80.0, 20000, null, null, null, null, null, Map.of(), wrongCols);

        assertThatThrownBy(() -> service.save("GENERAL_BEOTIMMOK", req))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void 대출금리는_0에서_15퍼센트_사이여야_한다() {
        List<List<Double>> tooHigh = List.of(
                List.of(2.5, 2.6, 2.7),
                List.of(2.7, 2.8, 2.9),
                List.of(3.0, 3.1, 3.2),
                List.of(3.3, 3.4, 20.0));
        LoanProductRequest req = new LoanProductRequest(19, 34, 5000, 6000, 33700, 20000, 85.0, 80.0, 20000, null, null, null, null, null, Map.of(), tooHigh);

        assertThatThrownBy(() -> service.save("GENERAL_BEOTIMMOK", req))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void 청년전용_버팀목_전세대출의_대출금리표는_4행_1열이다() {
        when(repository.findByLoanType("YOUTH_BEOTIMMOK")).thenReturn(Optional.empty());
        List<List<Double>> rateTable = List.of(List.of(2.2), List.of(2.5), List.of(2.9), List.of(3.3));
        LoanProductRequest req = new LoanProductRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, Map.of(), rateTable);

        LoanProductResponse res = service.save("YOUTH_BEOTIMMOK", req);

        assertThat(res.rateTable()).isEqualTo(rateTable);
    }

    @Test
    void 신생아_특례_버팀목대출의_대출금리표는_9행_4열이다() {
        when(repository.findByLoanType("NEWBORN_BEOTIMMOK")).thenReturn(Optional.empty());
        List<List<Double>> rateTable = List.of(
                List.of(1.30, 1.40, 1.50, 1.60), List.of(1.60, 1.70, 1.80, 1.90),
                List.of(1.90, 2.00, 2.10, 2.20), List.of(2.20, 2.30, 2.40, 2.50),
                List.of(2.55, 2.65, 2.75, 2.85), List.of(2.90, 3.00, 3.10, 3.20),
                List.of(3.25, 3.35, 3.45, 3.55), List.of(3.60, 3.70, 3.80, 3.90),
                List.of(4.00, 4.10, 4.20, 4.30));
        LoanProductRequest req = new LoanProductRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, Map.of(), rateTable);

        LoanProductResponse res = service.save("NEWBORN_BEOTIMMOK", req);

        assertThat(res.rateTable()).isEqualTo(rateTable);
    }

    @Test
    void 대출금리표를_지원하지_않는_대출에_보내면_검증_오류() {
        when(repository.findByLoanType("YOUTH_MONTHLY_RENT")).thenReturn(Optional.empty());
        List<List<Double>> rateTable = List.of(
                List.of(2.5, 2.6, 2.7), List.of(2.7, 2.8, 2.9), List.of(3.0, 3.1, 3.2), List.of(3.3, 3.4, 3.5));
        LoanProductRequest req = new LoanProductRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, Map.of(), rateTable);

        assertThatThrownBy(() -> service.save("YOUTH_MONTHLY_RENT", req))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void 저장된_대출금리표_JSON이_깨져_있어도_기본값으로_내려온다() {
        LoanProduct broken = LoanProduct.of(com.customhouse.domain.loan.entity.LoanType.GENERAL_BEOTIMMOK);
        broken.update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, "{not json");
        when(repository.findByLoanType(any())).thenReturn(Optional.empty());
        when(repository.findByLoanType("GENERAL_BEOTIMMOK")).thenReturn(Optional.of(broken));

        List<LoanProductResponse> loans = service.getAll().loans();

        assertThat(loans.get(0).rateTable()).isNull();
    }

    @Test
    void 이미_저장된_대출은_새로_만들지_않고_덮어쓴다() {
        LoanProduct existing = LoanProduct.of(com.customhouse.domain.loan.entity.LoanType.GENERAL_BEOTIMMOK);
        when(repository.findByLoanType("GENERAL_BEOTIMMOK")).thenReturn(Optional.of(existing));

        service.save("GENERAL_BEOTIMMOK", request(null, 39, Map.of()));

        ArgumentCaptor<LoanProduct> captor = ArgumentCaptor.forClass(LoanProduct.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(existing.getMaxAge()).isEqualTo(39);
        assertThat(existing.getMinAge()).isNull();
    }

    @Test
    void 최소_나이가_최대_나이보다_크면_검증_오류() {
        assertThatThrownBy(() -> service.save("YOUTH_BEOTIMMOK", request(40, 30, Map.of())))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).save(any());
    }

    @Test
    void 최대_대출금_비율한도와_최대_대출금액을_저장하고_비워두면_제한_없음이다() {
        LoanProductResponse limited = service.save("GENERAL_BEOTIMMOK", request(19, 39, Map.of()));
        assertThat(limited.maxLoanRatioPercent()).isEqualTo(80.0);
        assertThat(limited.maxLoanAmount()).isEqualTo(20000);

        LoanProductRequest noLimit = new LoanProductRequest(19, 39, 5000, 6000, 33700, 20000, 85.0, null, null, null, null, null, null, null, Map.of(), null);
        LoanProductResponse res = service.save("YOUTH_BEOTIMMOK", noLimit);
        assertThat(res.maxLoanRatioPercent()).isNull();
        assertThat(res.maxLoanAmount()).isNull();
    }

    @Test
    void 참고_확인_페이지_주소만_저장해도_대출_조건은_저장됨으로_바뀌지_않는다() {
        // 링크만 저장했다고 그 대출이 모든 매물에 적용되는 "저장됨" 상태가 되면 안 된다 (LoanProduct와 별개 저장소)
        when(repository.findByLoanType("GENERAL_BEOTIMMOK")).thenReturn(Optional.empty());
        when(referenceLinkRepository.findByLoanType("GENERAL_BEOTIMMOK")).thenReturn(Optional.empty());

        LoanProductResponse res = service.saveReferenceUrl("GENERAL_BEOTIMMOK",
                new com.customhouse.domain.loan.dto.LoanReferenceUrlRequest("https://example.com/loan-info"));

        assertThat(res.saved()).isFalse();
        assertThat(res.referenceUrl()).isEqualTo("https://example.com/loan-info");
        verify(repository, never()).save(any());
        ArgumentCaptor<LoanReferenceLink> captor = ArgumentCaptor.forClass(LoanReferenceLink.class);
        verify(referenceLinkRepository).save(captor.capture());
        assertThat(captor.getValue().getReferenceUrl()).isEqualTo("https://example.com/loan-info");
    }

    @Test
    void 참고_확인_페이지_주소는_목록_조회에도_함께_내려온다() {
        LoanReferenceLink link = LoanReferenceLink.of(com.customhouse.domain.loan.entity.LoanType.GENERAL_BEOTIMMOK, "https://example.com/a");
        when(repository.findByLoanType(any())).thenReturn(Optional.empty());
        when(referenceLinkRepository.findAllByLoanTypeIn(any())).thenReturn(List.of(link));

        LoanProductResponse general = service.getAll().loans().stream()
                .filter(l -> l.type().equals("GENERAL_BEOTIMMOK")).findFirst().orElseThrow();
        assertThat(general.referenceUrl()).isEqualTo("https://example.com/a");
        assertThat(general.saved()).isFalse();  // 조건은 여전히 미설정
    }

    @Test
    void 주소를_지운_뒤에도_목록_조회가_실패하지_않는다() {
        // 주소를 지워도 LoanReferenceLink 행 자체는 남는다(참고_확인_페이지_주소만_저장해도... 테스트와 동일 이유) -
        // referenceUrl이 null인 행이 섞여 있어도 getAll()이 죽으면 안 된다 (실제로 Collectors.toMap에서 NPE가 났었다)
        LoanReferenceLink cleared = LoanReferenceLink.of(com.customhouse.domain.loan.entity.LoanType.GENERAL_BEOTIMMOK, null);
        when(repository.findByLoanType(any())).thenReturn(Optional.empty());
        when(referenceLinkRepository.findAllByLoanTypeIn(any())).thenReturn(List.of(cleared));

        LoanListResponse res = service.getAll();

        LoanProductResponse general = res.loans().stream().filter(l -> l.type().equals("GENERAL_BEOTIMMOK")).findFirst().orElseThrow();
        assertThat(general.referenceUrl()).isNull();
        assertThat(res.loans()).hasSize(4);
    }

    @Test
    void 빈_주소로_저장하면_기존_참고_주소를_지운다() {
        LoanReferenceLink existing = LoanReferenceLink.of(com.customhouse.domain.loan.entity.LoanType.GENERAL_BEOTIMMOK, "https://old.example.com");
        when(referenceLinkRepository.findByLoanType("GENERAL_BEOTIMMOK")).thenReturn(Optional.of(existing));
        when(repository.findByLoanType("GENERAL_BEOTIMMOK")).thenReturn(Optional.empty());

        LoanProductResponse res = service.saveReferenceUrl("GENERAL_BEOTIMMOK",
                new com.customhouse.domain.loan.dto.LoanReferenceUrlRequest(""));

        assertThat(res.referenceUrl()).isNull();
        assertThat(existing.getReferenceUrl()).isNull();
    }

    @Test
    void 정해진_항목이_아닌_우대사항_키는_거부한다() {
        Map<String, LoanPreference> prefs = Map.of("HACK", new LoanPreference(true, 1.0, null, null, null, null, null, null));

        assertThatThrownBy(() -> service.save("YOUTH_BEOTIMMOK", request(19, 34, prefs)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).save(any());
    }

    @Test
    void 알_수_없는_대출_종류는_NOT_FOUND() {
        assertThatThrownBy(() -> service.save("NOPE", request(19, 34, Map.of())))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.delete("NOPE"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 삭제는_저장된_조건을_지우고_없어도_오류가_없다() {
        service.delete("NEWBORN_BEOTIMMOK");
        service.delete("NEWBORN_BEOTIMMOK");

        verify(repository, org.mockito.Mockito.times(2)).deleteByLoanType("NEWBORN_BEOTIMMOK");
    }

    @Test
    void 저장된_JSON이_깨져_있어도_목록은_기본값으로_내려온다() {
        LoanProduct broken = LoanProduct.of(com.customhouse.domain.loan.entity.LoanType.GENERAL_BEOTIMMOK);
        broken.update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, "{not json", null);
        when(repository.findByLoanType(any())).thenReturn(Optional.empty());
        when(repository.findByLoanType("GENERAL_BEOTIMMOK")).thenReturn(Optional.of(broken));

        List<LoanProductResponse> loans = service.getAll().loans();

        assertThat(loans.get(0).saved()).isTrue();
        assertThat(loans.get(0).preferences()).hasSize(LoanPreferenceKey.values().length);
    }
}
