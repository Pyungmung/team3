package com.customhouse.domain.policy.service;

// [담당: 송귀성] 관심정책 담기/조회/삭제 - 중복 담기는 한 건만, 없는 정책은 거절, 회원당 한도, 목록은 정책의 "현재" 내용으로 돌려준다 (2026-10-08).

import com.customhouse.domain.housingpolicy.entity.HousingPolicy;
import com.customhouse.domain.housingpolicy.repository.HousingPolicyRepository;
import com.customhouse.domain.policy.dto.FavoritePolicyResponse;
import com.customhouse.domain.policy.entity.FavoritePolicy;
import com.customhouse.domain.policy.repository.FavoritePolicyRepository;
import com.customhouse.global.error.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FavoritePolicyServiceTest {

    private static final Long USER = 7L;

    private FavoritePolicyRepository favorites;
    private HousingPolicyRepository policies;
    private FavoritePolicyService service;

    @BeforeEach
    void setUp() {
        favorites = mock(FavoritePolicyRepository.class);
        policies = mock(HousingPolicyRepository.class);
        service = new FavoritePolicyService(favorites, policies);
    }

    private HousingPolicy policy(long id, String name, String link) {
        HousingPolicy p = HousingPolicy.of("강남구", "서울시", name, "월 20만원", null, null, null, null, null,
                false, false, false, false, false, null, link);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    void 있는_정책을_처음_담으면_저장한다() {
        when(policies.existsById(5L)).thenReturn(true);

        service.add(USER, 5L);

        verify(favorites).saveAndFlush(any(FavoritePolicy.class));
    }

    @Test
    void 이미_담은_정책을_다시_담아도_오류_없이_그대로다() {
        when(favorites.existsByUserIdAndPolicyId(USER, 5L)).thenReturn(true);

        service.add(USER, 5L);

        verify(favorites, never()).saveAndFlush(any());
    }

    @Test
    void 삭제된_정책은_담을_수_없다() {
        when(policies.existsById(5L)).thenReturn(false);

        assertThatThrownBy(() -> service.add(USER, 5L)).isInstanceOf(CustomException.class);
        verify(favorites, never()).saveAndFlush(any());
    }

    @Test
    void 동시에_두_번_눌러_유니크_제약에_걸려도_성공으로_본다() {
        when(policies.existsById(5L)).thenReturn(true);
        when(favorites.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_favorite_policy_user_policy"));

        service.add(USER, 5L);
    }

    @Test
    void 회원당_한도를_넘으면_거절한다() {
        when(policies.existsById(5L)).thenReturn(true);
        when(favorites.countByUserId(USER)).thenReturn((long) FavoritePolicyService.MAX_FAVORITES_PER_USER);

        assertThatThrownBy(() -> service.add(USER, 5L)).isInstanceOf(CustomException.class);
    }

    @Test
    void 목록은_담은_순서의_반대로_정책의_현재_내용을_돌려주고_없어진_정책은_뺀다() {
        FavoritePolicy newer = FavoritePolicy.of(USER, 9L);
        FavoritePolicy older = FavoritePolicy.of(USER, 5L);
        FavoritePolicy orphan = FavoritePolicy.of(USER, 77L);   // 정책이 사라진 찌꺼기 행
        when(favorites.findByUserIdOrderByIdDesc(USER)).thenReturn(List.of(newer, orphan, older));
        when(policies.findAllById(any())).thenReturn(List.of(policy(5L, "청년월세지원", "https://a.go.kr"), policy(9L, "버팀목", null)));

        List<FavoritePolicyResponse> list = service.getMyFavorites(USER);

        assertThat(list).extracting(FavoritePolicyResponse::name).containsExactly("버팀목", "청년월세지원");
        assertThat(list.get(1).link()).isEqualTo("https://a.go.kr");
        assertThat(service.getMyPolicyIds(USER)).containsExactly(9L, 77L, 5L);
    }

    @Test
    void 삭제는_내_관심정책에서만_뺀다() {
        service.remove(USER, 5L);

        verify(favorites).deleteByUserIdAndPolicyId(USER, 5L);
    }
}
