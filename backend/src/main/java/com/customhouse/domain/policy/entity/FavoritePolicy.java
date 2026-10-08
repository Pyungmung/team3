package com.customhouse.domain.policy.entity;

import com.customhouse.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [담당: 송귀성] 주거정책 - 관심정책 (주거진단 리포트 "주거정책 추천" 표의 하트 -> 관심매물 페이지 "관심정책 조회").
 * 내용은 저장하지 않고 정책 id(housing_policies.id)만 들고 있다 - 관리자가 정책을 고치면 관심정책 조회에 바로 반영되고,
 * 정책이 삭제되면 이 행도 함께 지워진다(HousingPolicyService.delete). FK 없이 id만 둔다(CLAUDE.md 규칙).
 * (user_id, policy_id) 유니크라 중복 담기가 DB 차원에서 막힌다.
 */
@Entity
@Table(name = "favorite_policies",
        uniqueConstraints = @UniqueConstraint(name = "uk_favorite_policy_user_policy", columnNames = {"user_id", "policy_id"}),
        indexes = {
                @Index(name = "idx_favorite_policy_user", columnList = "user_id"),
                @Index(name = "idx_favorite_policy_policy", columnList = "policy_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoritePolicy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    public static FavoritePolicy of(Long userId, Long policyId) {
        FavoritePolicy fav = new FavoritePolicy();
        fav.userId = userId;
        fav.policyId = policyId;
        return fav;
    }
}
