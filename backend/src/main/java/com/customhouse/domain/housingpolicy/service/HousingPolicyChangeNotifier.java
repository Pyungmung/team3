package com.customhouse.domain.housingpolicy.service;

import com.customhouse.domain.housingpolicy.entity.HousingPolicy;
import com.customhouse.domain.notification.service.NotificationMailSender;
import com.customhouse.domain.notification.service.NotificationService;
import com.customhouse.domain.policy.entity.FavoritePolicy;
import com.customhouse.domain.policy.repository.FavoritePolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [담당: 송귀성] 주거정책이 수정/삭제되면 그 정책을 관심정책으로 담은 회원의 알림함(헤더 알림 벨 포함)에 알려준다 (2026-10-08).
 * 마이페이지 "알림 수신"을 켠 회원에게는 메일도 함께 보낸다(관심매물 알림 ListingAlertService와 같은 규칙, NotificationMailSender).
 * 알림/메일을 못 보내도 관리자의 저장/삭제는 그대로 진행한다 - 실패는 로그로만 남긴다.
 * 알림 내용은 500자 제한(notifications 테이블)이라 길면 자른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HousingPolicyChangeNotifier {

    private static final int CONTENT_MAX = 500;

    private final FavoritePolicyRepository favoritePolicyRepository;
    private final NotificationService notificationService;
    private final NotificationMailSender mailSender;

    /** 이 정책을 관심정책으로 담은 회원들 (삭제 전에 먼저 구해 둬야 한다 - 삭제하면 관심정책 행도 함께 지워진다). */
    public List<Long> watchers(Long policyId) {
        return favoritePolicyRepository.findByPolicyId(policyId).stream().map(FavoritePolicy::getUserId).toList();
    }

    /** 수정 알림: changedFields는 바뀐 항목 이름(예: "지원혜택", "링크"). 알림을 보낸 회원 수를 돌려준다. */
    public int policyChanged(HousingPolicy policy, List<String> changedFields, List<Long> watchers) {
        String changed = String.join(", ", changedFields);
        String message = "[" + label(policy) + "] " + changed + " 내용이 수정되었어요. 관심매물 > 관심정책 조회에서 확인해 보세요.";
        String mailBody = "관심 정책으로 담아두신 [" + label(policy) + "]의 " + changed + " 내용이 수정되었어요.\n\n"
                + "맞집 > 관심매물 > 관심정책 조회에서 바뀐 내용을 확인하실 수 있어요.";
        return send(watchers, "관심 정책 변경", message, "[맞집] 관심 정책 내용이 변경됐어요", mailBody);
    }

    /** 삭제 알림: 삭제된 정책은 관심정책에서도 함께 빠진다(그 삭제는 호출한 쪽이 한다). */
    public int policyDeleted(HousingPolicy policy, List<Long> watchers) {
        String message = "[" + label(policy) + "] 정책이 삭제(종료)되어 관심정책에서 빠졌어요.";
        String mailBody = "관심 정책으로 담아두신 [" + label(policy) + "] 정책이 삭제(종료)되어 관심정책에서 빠졌어요.\n\n"
                + "맞집 > 관심매물 > 알림함에서 확인하실 수 있어요.";
        return send(watchers, "관심 정책 삭제", message, "[맞집] 관심 정책이 삭제됐어요", mailBody);
    }

    private int send(List<Long> userIds, String title, String content, String mailSubject, String mailBody) {
        String text = content.length() > CONTENT_MAX ? content.substring(0, CONTENT_MAX - 1) + "…" : content;
        int sent = 0;
        for (Long userId : userIds) {
            try {
                notificationService.notify(userId, title, text);
                sent++;
            } catch (RuntimeException e) {
                log.warn("관심 정책 알림 처리에 실패했습니다 (회원: {}): {}", userId, e.getMessage());
                continue; // 알림함 알림을 못 쌓았으면 메일도 보내지 않는다
            }
            mailSender.sendIfEnabled(userId, mailSubject, mailBody);
        }
        return sent;
    }

    private static String label(HousingPolicy policy) {
        return policy.getAgency() + " · " + policy.getName();
    }
}
