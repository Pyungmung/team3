package com.customhouse.domain.user.service;

import com.customhouse.domain.user.dto.ChangePasswordRequest;
import com.customhouse.domain.user.dto.UpdateProfileRequest;
import com.customhouse.domain.user.dto.UserResponse;
import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [담당: 허겸] 회원 도메인 - 마이페이지 회원정보 수정 / 비밀번호 변경
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUser(userId);
        user.setNickname(request.nickname().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        return UserResponse.from(user);
    }

    /**
     * 현재 비밀번호가 맞아야 바꿀 수 있다. 네이버 가입 계정은 비밀번호가 없어 변경 불가.
     * 현재 비밀번호 불일치는 401(INVALID_CREDENTIALS)로 주면 프론트가 토큰 만료로 오해해 재발급을 시도하므로 400으로 응답한다.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getUser(userId);
        if (user.getPassword() == null) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "소셜 로그인 계정은 비밀번호를 변경할 수 없습니다.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "현재 비밀번호가 올바르지 않습니다.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "새 비밀번호가 현재 비밀번호와 같습니다.");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
