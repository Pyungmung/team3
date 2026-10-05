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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [담당: 송귀성] 추천 매물 - 허위매물 신고 서비스 테스트. 저장소/메일은 목으로 바꿔
 * 중복 신고 차단, 하루 상한, 2건 이상이면 flagged, 메일 실패해도 신고 유지를 확인한다.
 */
class ListingReportServiceTest {

    private static final String LISTING = "SEOCHO-202609-0001";

    private ListingReportRepository reportRepository;
    private UserRepository userRepository;
    private SupportMailService mailService;
    private ListingAlertService alertService;
    private ListingReportService service;

    @BeforeEach
    void setUp() {
        reportRepository = mock(ListingReportRepository.class);
        userRepository = mock(UserRepository.class);
        mailService = mock(SupportMailService.class);
        alertService = mock(ListingAlertService.class);
        service = new ListingReportService(reportRepository, userRepository, mailService, alertService);

        User user = new User();
        ReflectionTestUtils.setField(user, "email", "member@example.com");
        ReflectionTestUtils.setField(user, "nickname", "회원");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    private ListingReportRequest request(String contactEmail) {
        return new ListingReportRequest("FAKE_PRICE", "  월세가 다릅니다.  ", contactEmail, "서울 서초구 잠원로14길 42", "월세", 1000, 60);
    }

    @Test
    void 신고를_저장하고_관리자에게_메일을_보낸다() {
        when(reportRepository.countByListingId(LISTING)).thenReturn(1L);

        ListingReportStatus status = service.report(1L, LISTING, request(null));

        assertThat(status.count()).isEqualTo(1);
        assertThat(status.flagged()).isFalse();   // 1건은 아직 주의 표시가 아니다
        verify(reportRepository).saveAndFlush(any(ListingReport.class));
        verify(mailService).sendListingReport(eq("회원"), eq("member@example.com"), eq(LISTING), any(), eq(1L));
    }

    @Test
    void 두_건째_신고부터_flagged가_된다() {
        when(reportRepository.countByListingId(LISTING)).thenReturn(2L);

        assertThat(service.report(1L, LISTING, request(null)).flagged()).isTrue();
    }

    @Test
    void 신고가_경고_기준_2건에_처음_도달하면_관심매물_담은_회원에게_알린다() {
        when(reportRepository.countByListingId(LISTING)).thenReturn(2L);

        service.report(1L, LISTING, request(null));

        verify(alertService).notifyFlagged(LISTING, 2L);
    }

    @Test
    void 경고_기준_전이나_이후_신고에서는_알림을_다시_보내지_않는다() {
        when(reportRepository.countByListingId(LISTING)).thenReturn(1L, 3L);

        service.report(1L, LISTING, request(null));
        service.report(1L, LISTING, request(null));

        verify(alertService, never()).notifyFlagged(any(), anyLong());
    }

    @Test
    void 경고_알림_처리가_실패해도_신고는_유지된다() {
        when(reportRepository.countByListingId(LISTING)).thenReturn(2L);
        doThrow(new RuntimeException("boom")).when(alertService).notifyFlagged(any(), anyLong());

        assertThat(service.report(1L, LISTING, request(null)).flagged()).isTrue();
        verify(reportRepository).saveAndFlush(any(ListingReport.class));
    }

    @Test
    void 답장받을_이메일을_입력하면_회원_이메일_대신_쓴다() {
        when(reportRepository.countByListingId(LISTING)).thenReturn(1L);

        service.report(1L, LISTING, request("other@example.com"));

        verify(mailService).sendListingReport(any(), eq("other@example.com"), any(), any(), anyLong());
    }

    @Test
    void 같은_매물을_다시_신고하면_ALREADY_REPORTED이고_저장도_메일도_하지_않는다() {
        when(reportRepository.existsByListingIdAndUserId(LISTING, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.report(1L, LISTING, request(null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_REPORTED);
        verify(reportRepository, never()).saveAndFlush(any());
        verify(mailService, never()).sendListingReport(any(), any(), any(), any(), anyLong());
    }

    @Test
    void 동시에_눌러_유니크_제약에_걸려도_ALREADY_REPORTED로_처리한다() {
        when(reportRepository.saveAndFlush(any(ListingReport.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.report(1L, LISTING, request(null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_REPORTED);
        verify(mailService, never()).sendListingReport(any(), any(), any(), any(), anyLong());
    }

    @Test
    void 하루_신고_상한을_넘으면_REPORT_LIMIT_EXCEEDED() {
        when(reportRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn((long) ListingReportService.DAILY_REPORT_LIMIT);

        assertThatThrownBy(() -> service.report(1L, LISTING, request(null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_LIMIT_EXCEEDED);
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void 메일_발송이_실패해도_신고는_유지되고_성공으로_응답한다() {
        when(reportRepository.countByListingId(LISTING)).thenReturn(1L);
        doThrow(new CustomException(ErrorCode.MAIL_SEND_FAILED))
                .when(mailService).sendListingReport(any(), any(), any(), any(), anyLong());

        ListingReportStatus status = service.report(1L, LISTING, request(null));

        assertThat(status.count()).isEqualTo(1);
        verify(reportRepository).saveAndFlush(any(ListingReport.class));
    }

    @Test
    void 잘못된_매물번호는_VALIDATION_ERROR() {
        assertThatThrownBy(() -> service.report(1L, "../etc/passwd", request(null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void 신고_수_조회는_신고가_있는_매물만_돌려준다() {
        when(reportRepository.countByListingIds(List.of("A-1", "B-2")))
                .thenReturn(List.<Object[]>of(new Object[]{"B-2", 3L}));

        Map<String, ListingReportStatus> result = service.getStatuses(List.of("A-1", "B-2"));

        assertThat(result).containsOnlyKeys("B-2");
        assertThat(result.get("B-2")).isEqualTo(new ListingReportStatus(3, true));
    }
}
