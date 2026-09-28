package com.customhouse.domain.mypage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [담당: 황진구] 마이페이지 도메인 - 주거 조건의 우대사항 항목(다중 선택) 1건.
 *
 * 예전에는 HousingCondition.preferentialStatuses를 JPA @ElementCollection(값 컬렉션)으로 매핑해서
 * 자체 기본키가 없는 테이블(housing_condition_preferences)로 생성했다. H2/로컬 MySQL에서는 문제없었지만,
 * Aiven 등 관리형 클라우드 MySQL은 안전을 위해 `sql_require_primary_key`가 켜져 있어 기본키 없는
 * 테이블 생성 자체가 거부됐다(2026-09-22 발견). 그래서 다른 도메인(예: domain.board의 PostMeta 등)과
 * 같은 방식으로 자체 id를 가진 진짜 엔티티로 바꿨다 - 어떤 MySQL 설정에서도 동작한다.
 * HousingCondition.getPreferentialStatuses()/setPreferentialStatuses()가 이 엔티티를 감춰서
 * 기존처럼 Set&lt;PreferentialStatus&gt;로만 다루면 되고, 나머지 코드(DTO/서비스)는 변경이 필요 없다.
 */
@Entity
@Table(
        name = "housing_condition_preferences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_housing_condition_preference",
                columnNames = {"housing_condition_id", "preferential_status"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HousingConditionPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "housing_condition_id")
    private HousingCondition housingCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferential_status", nullable = false, length = 30)
    private PreferentialStatus preferentialStatus;

    public static HousingConditionPreference of(HousingCondition housingCondition, PreferentialStatus preferentialStatus) {
        HousingConditionPreference preference = new HousingConditionPreference();
        preference.housingCondition = housingCondition;
        preference.preferentialStatus = preferentialStatus;
        return preference;
    }
}
