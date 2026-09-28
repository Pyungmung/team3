package com.customhouse.domain.user.repository;

import com.customhouse.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * [담당: 허겸] 회원 도메인 - User JPA Repository
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    boolean existsByEmail(String email);
}
