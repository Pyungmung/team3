package com.customhouse.domain.listing.service;

import com.customhouse.domain.listing.dto.ListingReportRequest;
import com.customhouse.domain.listing.dto.ListingReportStatus;
import com.customhouse.domain.listing.entity.ListingReport;
import com.customhouse.domain.listing.repository.ListingReportRepository;
import com.customhouse.domain.support.service.SupportMailService;
import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * [담당: 송귀성] 추천 매물 - 허위매물 신고 저장/집계.
 * 신고는 로그인한 회원만, 회원당 매물 1번만 할 수 있고(유니크 제약), 하루 신고 수에도 상한을 둔다.
 * 신고가 저장되면 관리자에게 메일을 보낸다. 메일 발송이 실패해도 신고 자체는 유지한다
 * (누적 신고 수 집계가 핵심이라, 메일 문제로 신고가 사라지면 안 된다 - 실패는 로그에만 남는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingReportService {

    /** 이 횟수 이상 신고된 매물에 "허위매물 주의" 표시를 한다. */
    public static final int FLAG_THRESHOLD = 2;
    /** 한 회원이 하루(24시간)에 할 수 있는 신고 수. */
    static final int DAILY_REPORT_LIMIT = 20;
    private static final Pattern LISTING_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{3,40}");

    private final ListingReportRepository reportRepository;
    private final UserRepository userRepository;
    private final SupportMailService supportMailService;

    /** @return 이 신고가 반영된 뒤의 매물 신고 현황 */
    public ListingReportStatus report(Long userId, String listingId, ListingReportRequest request) {
        if (listingId == null || !LISTING_ID_PATTERN.matcher(listingId).matches()) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "매물번호가 올바르지 않습니다.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        if (reportRepository.existsByListingIdAndUserId(listingId, userId)) {
            throw new CustomException(ErrorCode.ALREADY_REPORTED);
        }
        if (reportRepository.countByUserIdAndCreatedAtAfter(userId, LocalDateTime.now().minusDays(1)) >= DAILY_REPORT_LIMIT) {
            throw new CustomException(ErrorCode.REPORT_LIMIT_EXCEEDED);
        }

        try {
            reportRepository.saveAndFlush(ListingReport.of(listingId, userId, request.reportType(), request.message().trim()));
        } catch (DataIntegrityViolationException e) {
            // 동시에 두 번 눌러 exists 검사를 함께 통과한 경우 - 유니크 제약이 막아준다
            throw new CustomException(ErrorCode.ALREADY_REPORTED);
        }

        long total = reportRepository.countByListingId(listingId);
        String replyTo = request.contactEmail() != null && !request.contactEmail().isBlank()
                ? request.contactEmail().trim() : user.getEmail();
        try {
            supportMailService.sendListingReport(user.getNickname(), replyTo, listingId, request, total);
        } catch (CustomException e) {
            log.warn("허위매물 신고는 저장됐지만 관리자 메일 발송에 실패했습니다 (매물: {}, 회원: {})", listingId, userId);
        }
        return status(total);
    }

    /** 신고가 1건 이상 있는 매물만 담아 돌려준다 (없는 매물은 프론트가 신고 0건으로 본다). */
    public Map<String, ListingReportStatus> getStatuses(Collection<String> listingIds) {
        Map<String, ListingReportStatus> result = new HashMap<>();
        if (listingIds == null || listingIds.isEmpty()) {
            return result;
        }
        List<Object[]> rows = reportRepository.countByListingIds(listingIds);
        for (Object[] row : rows) {
            result.put((String) row[0], status(((Number) row[1]).longValue()));
        }
        return result;
    }

    static ListingReportStatus status(long count) {
        return new ListingReportStatus(count, count >= FLAG_THRESHOLD);
    }
}
