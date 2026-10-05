package com.customhouse.domain.appsetting.repository;

import com.customhouse.domain.appsetting.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [담당: 송귀성] 기타 설정(싱글톤 1행) JPA Repository. 조회는 AppSetting.SINGLETON_ID로 findById 한다.
 */
public interface AppSettingRepository extends JpaRepository<AppSetting, Long> {
}
