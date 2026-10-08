package com.customhouse.domain.banner.service;

// [담당: 송귀성] 직접 배너 광고 서비스 - 입력 검증(위험 링크/자치구/자리 이름/기간), 공개 목록의 활성+기간 필터, 캐시 비움을 가짜 저장소로 확인한다 (2026-10-08).

import com.customhouse.domain.banner.dto.BannerRequest;
import com.customhouse.domain.banner.dto.BannerResponse;
import com.customhouse.domain.banner.entity.Banner;
import com.customhouse.domain.banner.repository.BannerRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BannerServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T03:00:00Z"), ZoneId.of("Asia/Seoul"));   // 2026-10-08

    private BannerRepository repository;
    private BannerService service;
    private final List<Banner> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = mock(BannerRepository.class);
        when(repository.save(any(Banner.class))).thenAnswer(inv -> {
            Banner b = inv.getArgument(0);
            if (!stored.contains(b)) {
                stored.add(b);
            }
            return b;
        });
        when(repository.findByActiveTrueOrderBySortOrderAscIdAsc()).thenAnswer(inv -> stored.stream().filter(Banner::isActive).toList());
        when(repository.findAllByOrderBySortOrderAscIdAsc()).thenAnswer(inv -> List.copyOf(stored));
        when(repository.findById(anyLong())).thenAnswer(inv -> stored.isEmpty() ? Optional.empty() : Optional.of(stored.get(0)));
        service = new BannerService(repository, CLOCK);
    }

    private static BannerRequest request(String link, String image, List<String> regions, List<String> slots, LocalDate start, LocalDate end, Boolean active) {
        return new BannerRequest("이사 특가", Banner.Category.MOVING, image, link, "이사 업체 배너", Banner.Segment.SAVING, regions,
                Banner.MoveWithin.IMMEDIATE, slots, start, end, active, 3);
    }

    private static BannerRequest ok() {
        return request("https://example.com/move", "/uploads/listings/a.png", List.of("서초구", "강남구"), List.of("home-bottom"), null, null, true);
    }

    @Test
    void 등록하면_조건이_정리되어_저장되고_응답에_배열로_나온다() {
        BannerResponse saved = service.create(request("https://example.com/move", "/uploads/listings/a.png", List.of(" 서초구 ", "강남구", "서초구", ""),
                List.of("home-bottom", "Report_Side".toLowerCase()), null, null, null));

        assertThat(saved.regions()).containsExactly("서초구", "강남구");
        assertThat(saved.slots()).containsExactly("home-bottom", "report_side");
        assertThat(saved.segment()).isEqualTo(Banner.Segment.SAVING);
        assertThat(saved.moveWithin()).isEqualTo(Banner.MoveWithin.IMMEDIATE);
        assertThat(saved.active()).isTrue();   // active를 안 보내면 켜진 상태로 등록
        assertThat(saved.sortOrder()).isEqualTo(3);
    }

    @Test
    void 조건을_비우면_제한_없음으로_저장된다() {
        BannerResponse saved = service.create(new BannerRequest("생필품 할인", Banner.Category.GROCERY, "https://cdn.example.com/b.jpg", "http://shop.example.com",
                null, null, null, null, null, null, null, null, null));

        assertThat(saved.segment()).isEqualTo(Banner.Segment.ALL);
        assertThat(saved.moveWithin()).isEqualTo(Banner.MoveWithin.ANY);
        assertThat(saved.regions()).isEmpty();
        assertThat(saved.slots()).isEmpty();
        assertThat(saved.altText()).isNull();
    }

    @Test
    void 위험한_링크와_이미지_주소는_거절한다() {
        for (String link : List.of("javascript:alert(1)", "ftp://example.com", "example.com", "//evil.example.com", "https://", "data:text/html,x")) {
            assertThatThrownBy(() -> service.create(request(link, "/uploads/listings/a.png", null, null, null, null, true)))
                    .as(link).isInstanceOf(CustomException.class).extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        }
        for (String image : List.of("javascript:alert(1)", "data:image/png;base64,AAAA", "uploads/a.png", "ftp://x/a.png")) {
            assertThatThrownBy(() -> service.create(request("https://example.com", image, null, null, null, null, true)))
                    .as(image).isInstanceOf(CustomException.class);
        }
        assertThat(stored).isEmpty();
    }

    @Test
    void 선택할_수_없는_자치구_자리_이름_기간은_거절한다() {
        assertThatThrownBy(() -> service.create(request("https://e.com", "/uploads/x.png", List.of("부산진구"), null, null, null, true)))
                .hasMessageContaining("부산진구");
        assertThatThrownBy(() -> service.create(request("https://e.com", "/uploads/x.png", null, List.of("Home Bottom!"), null, null, true)))
                .hasMessageContaining("자리 이름");
        assertThatThrownBy(() -> service.create(request("https://e.com", "/uploads/x.png", null, null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1), true)))
                .hasMessageContaining("종료일");
    }

    @Test
    void 공개_목록은_켜진_배너만_오늘_기간_안에서_내려준다() {
        service.create(request("https://e.com/1", "/uploads/1.png", null, null, null, null, true));                                      // 항상
        service.create(request("https://e.com/2", "/uploads/2.png", null, null, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 8), true));   // 오늘 하루(경계 포함)
        service.create(request("https://e.com/3", "/uploads/3.png", null, null, LocalDate.of(2026, 10, 9), null, true));                  // 내일부터
        service.create(request("https://e.com/4", "/uploads/4.png", null, null, null, LocalDate.of(2026, 10, 7), true));                  // 어제까지
        service.create(request("https://e.com/5", "/uploads/5.png", null, null, null, null, false));                                      // 꺼짐

        assertThat(service.listPublic()).extracting(BannerResponse::linkUrl).containsExactly("https://e.com/1", "https://e.com/2");
        assertThat(service.listAll()).hasSize(5);   // 관리자 목록은 전부
    }

    @Test
    void 공개_목록은_캐시하고_등록_수정_삭제하면_바로_반영한다() {
        service.create(request("https://e.com/1", "/uploads/1.png", null, null, null, null, true));
        service.listPublic();
        service.listPublic();
        verify(repository, times(1)).findByActiveTrueOrderBySortOrderAscIdAsc();   // 두 번째는 캐시

        service.create(request("https://e.com/2", "/uploads/2.png", null, null, null, null, true));   // 저장하면 캐시를 비운다(트랜잭션 밖이라 바로)
        assertThat(service.listPublic()).hasSize(2);

        service.update(1L, request("https://e.com/changed", "/uploads/1.png", null, null, null, null, false));
        assertThat(service.listPublic()).extracting(BannerResponse::linkUrl).containsExactly("https://e.com/2");

        service.delete(1L);
        verify(repository).delete(any(Banner.class));
    }

    @Test
    void 없는_배너를_수정_삭제하면_찾을_수_없다() {
        when(repository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, ok())).isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(CustomException.class);
    }
}
