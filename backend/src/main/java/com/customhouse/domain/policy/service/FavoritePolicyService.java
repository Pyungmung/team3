package com.customhouse.domain.policy.service;

import com.customhouse.domain.housingpolicy.entity.HousingPolicy;
import com.customhouse.domain.housingpolicy.repository.HousingPolicyRepository;
import com.customhouse.domain.policy.dto.FavoritePolicyResponse;
import com.customhouse.domain.policy.entity.FavoritePolicy;
import com.customhouse.domain.policy.repository.FavoritePolicyRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * [담당: 송귀성] 주거정책 - 관심정책 담기/조회/삭제. 이미 담은 정책을 다시 담아도 오류 없이 넘어간다(멱등).
 * 조회는 정책의 현재 내용을 housing_policies에서 읽어 돌려주므로 관리자가 고친 내용이 바로 반영된다.
 */
@Service
@RequiredArgsConstructor
public class FavoritePolicyService {

    /** 회원당 담을 수 있는 관심정책 수 (무한정 쌓이는 것을 막는다) */
    static final int MAX_FAVORITES_PER_USER = 200;

    private final FavoritePolicyRepository favoriteRepository;
    private final HousingPolicyRepository policyRepository;

    public void add(Long userId, Long policyId) {
        if (favoriteRepository.existsByUserIdAndPolicyId(userId, policyId)) {
            return;
        }
        if (!policyRepository.existsById(policyId)) {
            throw new CustomException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없어요. 삭제되었을 수 있어요. 새로고침해 주세요.");
        }
        if (favoriteRepository.countByUserId(userId) >= MAX_FAVORITES_PER_USER) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "관심 정책은 최대 " + MAX_FAVORITES_PER_USER + "건까지 담을 수 있어요.");
        }
        try {
            favoriteRepository.saveAndFlush(FavoritePolicy.of(userId, policyId));
        } catch (DataIntegrityViolationException e) {
            // 동시에 두 번 눌러 이미 담긴 경우 - 유니크 제약이 막아줬으니 성공으로 본다
        }
    }

    @Transactional(readOnly = true)
    public List<FavoritePolicyResponse> getMyFavorites(Long userId) {
        List<FavoritePolicy> favorites = favoriteRepository.findByUserIdOrderByIdDesc(userId);
        Map<Long, HousingPolicy> policies = policyRepository.findAllById(favorites.stream().map(FavoritePolicy::getPolicyId).toList())
                .stream().collect(Collectors.toMap(HousingPolicy::getId, Function.identity()));
        // 정책이 삭제되면 관심정책 행도 함께 지워지지만, 혹시 남은 행이 있어도 화면에는 내보내지 않는다
        return favorites.stream()
                .map(f -> policies.get(f.getPolicyId()))
                .filter(java.util.Objects::nonNull)
                .map(FavoritePolicyResponse::from)
                .toList();
    }

    /** 리포트 표의 하트 표시용: 내가 담은 정책 id 목록만 가볍게 돌려준다. */
    @Transactional(readOnly = true)
    public List<Long> getMyPolicyIds(Long userId) {
        return favoriteRepository.findByUserIdOrderByIdDesc(userId).stream().map(FavoritePolicy::getPolicyId).toList();
    }

    public void remove(Long userId, Long policyId) {
        favoriteRepository.deleteByUserIdAndPolicyId(userId, policyId);
    }
}
