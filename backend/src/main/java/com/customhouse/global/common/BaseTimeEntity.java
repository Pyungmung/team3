package com.customhouse.global.common;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * [담당: 허겸] 공통 인프라 - 모든 JPA 엔티티가 공통으로 갖는 생성/수정 시각 필드.
 * User 엔티티부터 실제 JPA @Entity로 전환되며 이 클래스가 활성화되었다 (JpaAuditingConfig 참고).
 * 아직 JPA로 전환되지 않은 다른 도메인 엔티티(POJO)가 상속해도 무해하다 (Hibernate가 관리하지 않는 클래스이므로).
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
