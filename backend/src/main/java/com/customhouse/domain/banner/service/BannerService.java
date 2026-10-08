package com.customhouse.domain.banner.service;

import com.customhouse.domain.banner.dto.BannerRequest;
import com.customhouse.domain.banner.dto.BannerResponse;
import com.customhouse.domain.banner.entity.Banner;
import com.customhouse.domain.banner.repository.BannerRepository;
import com.customhouse.global.common.TtlCache;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * [담당: 송귀성] 직접 배너 광고 (2026-10-08): 관리자 등록/수정/삭제와 공개 노출 목록.
 * 공개 목록은 "활성 + 오늘 노출 기간 안"인 배너만 내려주고, 활성 배너 전체를 60초 캐시한다(방문자마다 DB를 읽지 않게. 기간은 읽을 때마다 오늘 날짜로 거른다).
 * 관리자가 저장/삭제하면 캐시를 비운다. 링크는 http/https만 받는다(javascript: 같은 주소로 방문자를 속이는 것을 막는다).
 */
@Service
public class BannerService {

    /** 직장 자치구 조건으로 고를 수 있는 값 (프론트 address-match-util.js의 WORK_HUB_COORDS와 같다) */
    static final Set<String> REGIONS = Set.of("강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구", "노원구", "도봉구",
            "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구", "성북구", "송파구", "양천구", "영등포구", "용산구", "은평구", "종로구",
            "중구", "중랑구", "분당구");
    private static final Pattern HTTP_URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SLOT_NAME = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,39}$");

    private final BannerRepository repository;
    private final Clock clock;
    private final TtlCache<List<BannerResponse>> activeCache = new TtlCache<>(60 * 1000L);

    @Autowired
    public BannerService(BannerRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    BannerService(BannerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** 방문자에게 내려주는 노출 가능한 배너 (활성 + 오늘 기간 안) */
    public List<BannerResponse> listPublic() {
        LocalDate today = LocalDate.now(clock);
        return activeCache.get(() -> List.copyOf(repository.findByActiveTrueOrderBySortOrderAscIdAsc().stream().map(BannerResponse::of).toList()))
                .stream().filter(b -> inPeriod(b, today)).toList();
    }

    public List<BannerResponse> listAll() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream().map(BannerResponse::of).toList();
    }

    @Transactional
    public BannerResponse create(BannerRequest request) {
        Banner banner = Banner.create();
        applyRequest(banner, request);
        BannerResponse saved = BannerResponse.of(repository.save(banner));
        activeCache.evictAfterCommit();
        return saved;
    }

    @Transactional
    public BannerResponse update(Long id, BannerRequest request) {
        Banner banner = repository.findById(id).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "배너를 찾을 수 없어요."));
        applyRequest(banner, request);
        BannerResponse saved = BannerResponse.of(repository.save(banner));
        activeCache.evictAfterCommit();
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Banner banner = repository.findById(id).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "배너를 찾을 수 없어요."));
        repository.delete(banner);
        activeCache.evictAfterCommit();
    }

    private static boolean inPeriod(BannerResponse b, LocalDate today) {
        return (b.startsOn() == null || !today.isBefore(b.startsOn())) && (b.endsOn() == null || !today.isAfter(b.endsOn()));
    }

    private void applyRequest(Banner banner, BannerRequest r) {
        String title = r.title().trim();
        String imageUrl = r.imageUrl().trim();
        String linkUrl = r.linkUrl().trim();
        if (title.isEmpty()) {
            throw invalid("배너 제목을 입력해주세요.");
        }
        if (!HTTP_URL.matcher(linkUrl).matches()) {
            throw invalid("링크는 http:// 또는 https:// 로 시작하는 주소만 쓸 수 있어요.");
        }
        if (!(imageUrl.startsWith("/uploads/") || HTTP_URL.matcher(imageUrl).matches())) {
            throw invalid("배너 이미지는 업로드한 사진이거나 http(s) 주소여야 해요.");
        }
        if (r.startsOn() != null && r.endsOn() != null && r.endsOn().isBefore(r.startsOn())) {
            throw invalid("노출 종료일은 시작일보다 빠를 수 없어요.");
        }
        int sortOrder = r.sortOrder() == null ? 0 : r.sortOrder();
        if (sortOrder < 0 || sortOrder > 9999) {
            throw invalid("정렬 순서는 0~9999 사이로 입력해주세요.");
        }
        String altText = r.altText() == null || r.altText().isBlank() ? null : r.altText().trim();
        banner.apply(title, r.category(), imageUrl, linkUrl, altText,
                r.segment() == null ? Banner.Segment.ALL : r.segment(),
                joinRegions(r.regions()),
                r.moveWithin() == null ? Banner.MoveWithin.ANY : r.moveWithin(),
                joinSlots(r.slots()),
                r.startsOn(), r.endsOn(), r.active() == null || r.active(), sortOrder);
    }

    private static String joinRegions(List<String> regions) {
        if (regions == null || regions.isEmpty()) {
            return null;
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String region : regions) {
            String name = region == null ? "" : region.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (!REGIONS.contains(name)) {
                throw invalid("직장 자치구 '" + name + "'은(는) 선택할 수 없는 값이에요.");
            }
            cleaned.add(name);
        }
        return cleaned.isEmpty() ? null : String.join(",", cleaned);
    }

    private static String joinSlots(List<String> slots) {
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String slot : slots) {
            String name = slot == null ? "" : slot.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (!SLOT_NAME.matcher(name).matches()) {
                throw invalid("자리 이름 '" + name + "'은(는) 영문 소문자·숫자·-·_ 로 40자 이하여야 해요.");
            }
            cleaned.add(name);
        }
        String joined = String.join(",", cleaned);
        if (joined.length() > 300) {
            throw invalid("자리 이름이 너무 많아요.");
        }
        return joined.isEmpty() ? null : joined;
    }

    private static CustomException invalid(String message) {
        return new CustomException(ErrorCode.VALIDATION_ERROR, message);
    }
}
