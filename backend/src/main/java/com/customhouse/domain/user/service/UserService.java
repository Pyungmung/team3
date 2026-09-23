package com.customhouse.domain.user.service;

import com.customhouse.domain.mypage.repository.HousingConditionRepository;
import com.customhouse.domain.notification.repository.NotificationRepository;
import com.customhouse.domain.payment.repository.PaymentRepository;
import com.customhouse.domain.payment.repository.SubscriptionRepository;
import com.customhouse.domain.user.dto.ChangePasswordRequest;
import com.customhouse.domain.user.dto.DeleteAccountRequest;
import com.customhouse.domain.user.dto.MarketingConsentRequest;
import com.customhouse.domain.user.dto.UpdateProfileRequest;
import com.customhouse.domain.user.dto.UserResponse;
import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.domain.watchlist.repository.WatchlistItemRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [담당: 허겸] 회원 도메인 - 마이페이지 회원정보 수정 / 비밀번호 변경 / 회원 탈퇴
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // 회원 탈퇴 시 다른 도메인의 개인 데이터를 함께 정리하기 위해 주입한다 (아래 deleteAccount 참고).
    private final HousingConditionRepository housingConditionRepository;
    private final NotificationRepository notificationRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WatchlistItemRepository watchlistItemRepository;

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUser(userId);
        user.setNickname(request.nickname().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateMarketingConsent(Long userId, MarketingConsentRequest request) {
        User user = getUser(userId);
        user.setMarketingConsent(request.marketingConsent());
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

    /**
     * 회원 탈퇴. 이메일 가입(LOCAL) 계정은 비밀번호 확인이 반드시 필요하고(되돌릴 수 없는 작업이라 재확인),
     * 소셜(NAVER) 계정은 비밀번호가 없어 확인 없이 진행한다.
     *
     * 개인 데이터(주거 조건, 알림함, 결제/구독 이력, 관심 매물)는 함께 삭제한다. 반면 커뮤니티 게시글/댓글/
     * 좋아요/투표/스크랩은 일부러 남겨둔다 - 게시글에는 다른 사용자의 댓글이 달려있을 수 있어 통째로
     * 지우면 남의 글까지 사라지고, board 쪽 화면은 이미 존재하지 않는 writerId를 "알 수 없음"으로 표시하도록
     * 되어 있어(BoardSupport) 남겨둬도 깨지지 않는다. properties/registry_logs도 매물 자체의 데이터라
     * 여러 사용자가 공유하므로 건드리지 않는다.
     *
     * 활성 구독이 있으면 삭제한다 - 행 자체가 없어지면 SubscriptionScheduler의 자동결제 대상 조회에
     * 걸리지 않으니 이후 재청구는 안 되지만, 실제 토스페이먼츠 빌링키 해지 API 호출은 아직 연동돼 있지
     * 않다(TODO, 담당: 황진구). SubscriptionService.cancel()을 쓰지 않는 이유: 그 메서드는 구독이
     * 없으면 예외를 던지는데, 같은 @Transactional 안에서 호출하면 여기서 잡아도 트랜잭션이 이미
     * rollback-only로 표시돼 뒤이은 삭제가 전부 롤백된다 (Spring의 흔한 함정) - 그래서 직접 지운다.
     */
    @Transactional
    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        User user = getUser(userId);

        if (user.getPassword() != null) {
            String password = request.password();
            if (password == null || password.isBlank() || !passwordEncoder.matches(password, user.getPassword())) {
                throw new CustomException(ErrorCode.VALIDATION_ERROR, "비밀번호가 올바르지 않습니다.");
            }
        }

        subscriptionRepository.findByUserId(userId).ifPresent(subscriptionRepository::delete);
        paymentRepository.deleteByUserId(userId);
        watchlistItemRepository.deleteByUserId(userId);
        notificationRepository.deleteByUserId(userId);
        // preferentialStatuses(HousingConditionPreference)는 HousingCondition에 cascade+orphanRemoval로
        // 걸려있어 아래 delete 한 번으로 함께 지워진다.
        housingConditionRepository.findByUserId(userId).ifPresent(housingConditionRepository::delete);

        userRepository.delete(user);
        log.info("회원 탈퇴 처리 완료 (userId={})", userId);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
