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
        return new LoanProductRequest(minAge, maxAge, 5000, 6000, 33700, 20000, 85.0, 80.0, 20000, prefs);
    }

    @Test
    void 대출_5종은_저장이_없어도_모두_내려오고_우대사항이_전부_채워진다() {
        when(repository.findByLoanType(any())).thenReturn(Optional.empty());

        LoanListResponse res = service.getAll();

        assertThat(res.loans()).hasSize(5).allSatisfy(l -> {
            assertThat(l.saved()).isFalse();
            assertThat(l.preferences()).hasSize(LoanPreferenceKey.values().length);
        });
        assertThat(res.preferenceOptions()).extracting("key")
                .containsExactly("BASIC_LIVELIHOOD", "NEAR_POOR", "SINGLE_PARENT", "INDEPENDENT_YOUTH", "NEWLYWED", "MULTI_CHILD", "NO_HOME", "SME_EMPLOYED_YOUTH");
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
        prefs.put("NEWLYWED", new LoanPreference(true, 0.2));

        LoanProductResponse res = service.save("YOUTH_BEOTIMMOK", request(19, 34, prefs));

        assertThat(res.saved()).isTrue();
        assertThat(res.minAge()).isEqualTo(19);
        assertThat(res.maxAge()).isEqualTo(34);
        assertThat(res.maxAsset()).isEqualTo(33700);
        assertThat(res.maxListingDeposit()).isEqualTo(20000);
        assertThat(res.maxExclusiveArea()).isEqualTo(85.0);
        assertThat(res.maxLoanRatioPercent()).isEqualTo(80.0);
        assertThat(res.maxLoanAmount()).isEqualTo(20000);
        assertThat(res.preferences().get("NEWLYWED")).isEqualTo(new LoanPreference(true, 0.2));
        assertThat(res.preferences().get("NO_HOME")).isEqualTo(LoanPreference.EMPTY);
        assertThat(res.preferences()).hasSize(LoanPreferenceKey.values().length);
        assertThat(res.preferences().get("SME_EMPLOYED_YOUTH")).isEqualTo(LoanPreference.EMPTY);
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

        LoanProductRequest noLimit = new LoanProductRequest(19, 39, 5000, 6000, 33700, 20000, 85.0, null, null, Map.of());
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
        assertThat(res.loans()).hasSize(5);
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
        Map<String, LoanPreference> prefs = Map.of("HACK", new LoanPreference(true, 1.0));

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
        broken.update(null, null, null, null, null, null, null, null, null, "{not json");
        when(repository.findByLoanType(any())).thenReturn(Optional.empty());
        when(repository.findByLoanType("GENERAL_BEOTIMMOK")).thenReturn(Optional.of(broken));

        List<LoanProductResponse> loans = service.getAll().loans();

        assertThat(loans.get(0).saved()).isTrue();
        assertThat(loans.get(0).preferences()).hasSize(LoanPreferenceKey.values().length);
    }
}
