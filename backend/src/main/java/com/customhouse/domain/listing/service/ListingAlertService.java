package com.customhouse.domain.listing.service;

import com.customhouse.domain.listing.entity.ListingFavorite;
import com.customhouse.domain.listing.repository.ListingFavoriteRepository;
import com.customhouse.domain.mypage.repository.HousingConditionRepository;
import com.customhouse.domain.notification.service.NotificationService;
import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.mail.MailClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * [담당: 송귀성] 관심매물 알림 - 하트로 담은 관심매물에 생긴 일(가격 변동, 허위매물 경고)을 알림함과 메일로 알린다 (2026-10-05).
 *
 * - 알림함: 그 매물을 관심매물로 담은 모든 회원에게 항상 쌓는다 (notifications 테이블, 휴대폰 알림바처럼 읽고 지울 수 있다).
 * - 메일: 마이페이지 "알림 수신"(HousingCondition.notificationEnabled)을 켠 회원에게만 보낸다. 메일 발송이 실패해도
 *   알림함 알림과 가격 갱신은 유지하고 실패는 로그에만 남긴다 (SendGrid 장애로 매물 수정/신고가 막히면 안 된다).
 * - 이전에 쓰던 별도 "관심 등록(properties/watchlist)" 방식의 가격 재확인 알림은 이 방식으로 대체되어 삭제했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingAlertService {

    private final ListingFavoriteRepository favoriteRepository;
    private final NotificationService notificationService;
    private final HousingConditionRepository housingConditionRepository;
    private final UserRepository userRepository;
    private final MailClient mailClient;

    /**
     * 매물 가격(보증금/월세)이 바뀌었을 때 호출한다. 이 매물을 담은 회원 중 저장된 가격과 새 가격이 다른 회원만
     * 알림을 받고, 그 회원의 관심매물 가격을 새 값으로 갱신한다. 전세는 월세 null/0을 같은 값으로 본다.
     *
     * @return 알림을 만든 회원 수
     */
    @Transactional
    public int notifyPriceChange(String listingId, Integer newDeposit, Integer newMonthlyRent) {
        int deposit = newDeposit == null ? 0 : newDeposit;
        int rent = newMonthlyRent == null ? 0 : newMonthlyRent;
        int notified = 0;
        for (ListingFavorite fav : favoriteRepository.findByListingId(listingId)) {
            int oldDeposit = fav.getDeposit() == null ? 0 : fav.getDeposit();
            int oldRent = fav.getMonthlyRent() == null ? 0 : fav.getMonthlyRent();
            if (oldDeposit == deposit && oldRent == rent) {
                continue;
            }
            fav.applyPriceChange(deposit, rent);
            favoriteRepository.save(fav);

            String change = describePriceChange(oldDeposit, deposit, oldRent, rent);
            String place = placeName(fav);
            notificationService.notify(fav.getUserId(), "관심 매물 가격 변동", "[" + place + "] " + change);
            sendMailIfEnabled(fav.getUserId(), "[맞집] 관심 매물 가격이 변동됐어요",
                    "관심 매물로 담아두신 [" + place + "]의 가격이 변동됐어요.\n\n" + change
                            + "\n\n맞집 > 관심매물 > 알림함에서 확인하실 수 있어요.");
            notified++;
        }
        return notified;
    }

    /** 매물의 허위매물 신고가 경고 기준(ListingReportService.FLAG_THRESHOLD)에 처음 도달했을 때 호출한다. */
    @Transactional(readOnly = true)
    public int notifyFlagged(String listingId, long reportCount) {
        List<ListingFavorite> favorites = favoriteRepository.findByListingId(listingId);
        for (ListingFavorite fav : favorites) {
            String place = placeName(fav);
            String message = "다른 사용자들의 신고가 " + reportCount + "건 누적되어 허위매물 주의 표시가 추가되었어요.";
            notificationService.notify(fav.getUserId(), "관심 매물 허위매물 경고", "[" + place + "] " + message);
            sendMailIfEnabled(fav.getUserId(), "[맞집] 관심 매물에 허위매물 주의 표시가 생겼어요",
                    "관심 매물로 담아두신 [" + place + "]에 대해\n" + message
                            + "\n\n계약 전에 매물 정보를 꼭 다시 확인해 주세요."
                            + "\n맞집 > 관심매물 > 알림함에서 확인하실 수 있어요.");
        }
        return favorites.size();
    }

    static String describePriceChange(int oldDeposit, int newDeposit, int oldRent, int newRent) {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.KOREA);
        StringBuilder sb = new StringBuilder();
        if (oldDeposit != newDeposit) {
            sb.append("보증금 ").append(nf.format(oldDeposit)).append(" → ").append(nf.format(newDeposit)).append("만원");
        }
        if (oldRent != newRent) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("월세 ").append(nf.format(oldRent)).append(" → ").append(nf.format(newRent)).append("만원");
        }
        return sb.toString();
    }

    private static String placeName(ListingFavorite fav) {
        String name = firstNonBlank(fav.getBuildingName(), fav.getAddress(), fav.getListingId());
        String unit = fav.getUnitLabel();
        return unit != null && !unit.isBlank() && !Objects.equals(unit, name) ? name + " " + unit : name;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    /** 알림 수신을 켠 회원에게만 메일을 보낸다. 실패는 삼키고 로그만 남긴다. */
    private void sendMailIfEnabled(Long userId, String subject, String text) {
        try {
            boolean enabled = housingConditionRepository.findByUserId(userId)
                    .map(c -> c.isNotificationEnabled())
                    .orElse(false);
            if (!enabled) {
                return;
            }
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                return;
            }
            mailClient.send(user.getEmail(), subject, text, null);
        } catch (CustomException e) {
            log.warn("관심매물 알림 메일 발송 실패 (회원: {}): {}", userId, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("관심매물 알림 메일 처리 중 오류 (회원: {}): {}", userId, e.toString());
        }
    }
}
