package com.customhouse.domain.policy.repository;

import com.customhouse.domain.policy.entity.FavoritePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * [담당: 송귀성] 주거정책 - 관심정책 JPA Repository
 */
public interface FavoritePolicyRepository extends JpaRepository<FavoritePolicy, Long> {

    boolean existsByUserIdAndPolicyId(Long userId, Long policyId);

    long countByUserId(Long userId);

    List<FavoritePolicy> findByUserIdOrderByIdDesc(Long userId);

    /** 이 정책을 관심정책으로 담은 모든 회원 (정책 수정/삭제 알림 대상). */
    List<FavoritePolicy> findByPolicyId(Long policyId);

    @Transactional
    void deleteByUserIdAndPolicyId(Long userId, Long policyId);

    /** 정책이 삭제될 때 그 정책을 담은 모든 회원의 관심정책에서 함께 뺀다. */
    @Transactional
    void deleteByPolicyId(Long policyId);

    /** 회원 탈퇴 시 함께 정리한다 (domain.user.service.UserService 참고). */
    @Transactional
    void deleteByUserId(Long userId);
}
