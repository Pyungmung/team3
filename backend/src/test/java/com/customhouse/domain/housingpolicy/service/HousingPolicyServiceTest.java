package com.customhouse.domain.housingpolicy.service;

// [담당: 송귀성] 주거지원정책 추가/수정/삭제 - 수정하면 바뀐 항목만 골라 관심정책 담은 회원에게 알리고, 삭제하면 관심정책에서 빼고 알리며,
// 진단 요청용 목록 캐시가 저장/삭제 때 갱신되는지 확인한다 (2026-10-08).

import com.customhouse.domain.housingpolicy.dto.HousingPolicyForEngine;
import com.customhouse.domain.housingpolicy.dto.HousingPolicyRequest;
import com.customhouse.domain.housingpolicy.dto.HousingPolicySaveResponse;
import com.customhouse.domain.housingpolicy.entity.HousingPolicy;
import com.customhouse.domain.housingpolicy.repository.HousingPolicyRepository;
import com.customhouse.domain.policy.repository.FavoritePolicyRepository;
import com.customhouse.global.error.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HousingPolicyServiceTest {

    private HousingPolicyRepository repository;
    private FavoritePolicyRepository favorites;
    private HousingPolicyChangeNotifier notifier;
    private HousingPolicyService service;

    @BeforeEach
    void setUp() {
        repository = mock(HousingPolicyRepository.class);
        favorites = mock(FavoritePolicyRepository.class);
        notifier = mock(HousingPolicyChangeNotifier.class);
        when(repository.saveAndFlush(any(HousingPolicy.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new HousingPolicyService(repository, favorites, notifier);
    }

    private HousingPolicy existing() {
        return HousingPolicy.of("강남구", "서울시", "청년월세지원", "월 20만원", 19, 39, 5000, 34500, null,
                false, false, false, true, false, "메모", "https://old.go.kr");
    }

    /** 기존 정책과 같은 값의 요청에서 필드 몇 개만 바꿔 본다 */
    private HousingPolicyRequest request(String description, Integer maxAge, boolean requireSme, String note, String link) {
        return new HousingPolicyRequest("강남구", "서울시", "청년월세지원", description, 19, maxAge, 5000, 34500, null,
                false, requireSme, false, true, false, note, link);
    }

    @Test
    void 내용이_바뀌면_바뀐_항목_이름으로_관심정책_담은_회원에게_알린다() {
        when(repository.findById(5L)).thenReturn(Optional.of(existing()));
        when(notifier.watchers(any())).thenReturn(List.of(11L, 12L));
        when(notifier.policyChanged(any(), anyList(), anyList())).thenReturn(2);

        HousingPolicySaveResponse response = service.update(5L, request("월 25만원", 39, false, "메모", "https://old.go.kr"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> fields = ArgumentCaptor.forClass(List.class);
        verify(notifier).policyChanged(any(), fields.capture(), eq(List.of(11L, 12L)));
        assertThat(fields.getValue()).containsExactly("지원혜택");
        assertThat(response.notifiedUsers()).isEqualTo(2);
        assertThat(response.policy().description()).isEqualTo("월 25만원");
    }

    @Test
    void 여러_항목이_바뀌면_표의_칸_순서대로_모두_알린다() {
        when(repository.findById(5L)).thenReturn(Optional.of(existing()));

        service.update(5L, request("월 20만원", 34, true, "메모", "https://new.go.kr"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> fields = ArgumentCaptor.forClass(List.class);
        verify(notifier).policyChanged(any(), fields.capture(), anyList());
        assertThat(fields.getValue()).containsExactly("나이 조건", "중소기업 조건", "링크");
    }

    @Test
    void 관리자용_메모만_바뀌거나_아무것도_안_바뀌면_알림을_보내지_않는다() {
        when(repository.findById(5L)).thenReturn(Optional.of(existing()));

        HousingPolicySaveResponse memoOnly = service.update(5L, request("월 20만원", 39, false, "새 메모", "https://old.go.kr"));
        HousingPolicySaveResponse same = service.update(5L, request("  월 20만원  ", 39, false, "새 메모", "  https://old.go.kr "));

        verify(notifier, never()).policyChanged(any(), anyList(), anyList());
        assertThat(memoOnly.notifiedUsers()).isZero();
        assertThat(same.notifiedUsers()).isZero();
        assertThat(memoOnly.policy().note()).isEqualTo("새 메모");   // 메모는 저장된다
    }

    @Test
    void 없는_정책을_수정하거나_삭제하면_NOT_FOUND다() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request("x", 39, false, null, null))).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(CustomException.class);
    }

    @Test
    void 삭제하면_관심정책에서_빼고_담았던_회원에게_알린다() {
        HousingPolicy policy = existing();
        when(repository.findById(5L)).thenReturn(Optional.of(policy));
        when(notifier.watchers(5L)).thenReturn(List.of(11L));
        when(notifier.policyDeleted(policy, List.of(11L))).thenReturn(1);

        HousingPolicySaveResponse response = service.delete(5L);

        verify(favorites).deleteByPolicyId(5L);
        verify(repository).delete(policy);
        assertThat(response.notifiedUsers()).isEqualTo(1);
        assertThat(response.policy()).isNull();
    }

    @Test
    void 담은_회원이_없으면_삭제해도_알림은_없다() {
        when(repository.findById(5L)).thenReturn(Optional.of(existing()));
        when(notifier.watchers(5L)).thenReturn(List.of());

        service.delete(5L);

        verify(notifier, never()).policyDeleted(any(), anyList());
    }

    @Test
    void 추가는_알림_없이_저장하고_빈_링크와_메모는_null로_정리한다() {
        HousingPolicySaveResponse response = service.create(request("월 20만원", 39, false, "  ", ""));

        assertThat(response.notifiedUsers()).isZero();
        assertThat(response.policy().link()).isNull();
        assertThat(response.policy().note()).isNull();
        verify(notifier, never()).policyChanged(any(), anyList(), anyList());
    }

    @Test
    void 진단용_목록은_캐시하고_저장_삭제하면_다시_읽는다() {
        HousingPolicy policy = existing();
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(policy));
        when(repository.findById(5L)).thenReturn(Optional.of(policy));

        List<HousingPolicyForEngine> first = service.getForEngine();
        service.getForEngine();
        verify(repository, times(1)).findAllByOrderByIdAsc();   // 두 번째는 캐시

        service.update(5L, request("월 25만원", 39, false, "메모", "https://old.go.kr"));
        service.getForEngine();
        verify(repository, times(2)).findAllByOrderByIdAsc();   // 저장하면 비워져 다시 읽는다

        assertThat(first).hasSize(1);
        assertThat(first.get(0).name()).isEqualTo("청년월세지원");
    }
}
