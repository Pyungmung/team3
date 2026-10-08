package com.customhouse.domain.housingpolicy.service;

import com.customhouse.domain.housingpolicy.dto.HousingPolicyForEngine;
import com.customhouse.domain.housingpolicy.dto.HousingPolicyRequest;
import com.customhouse.domain.housingpolicy.dto.HousingPolicyResponse;
import com.customhouse.domain.housingpolicy.dto.HousingPolicySaveResponse;
import com.customhouse.domain.housingpolicy.entity.HousingPolicy;
import com.customhouse.domain.housingpolicy.repository.HousingPolicyRepository;
import com.customhouse.domain.policy.repository.FavoritePolicyRepository;
import com.customhouse.global.common.TtlCache;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * [담당: 송귀성] 주거지원정책 조회/추가/수정/삭제 (관리자 수정 > 주거지원정책).
 * 수정/삭제하면 그 정책을 관심정책으로 담은 회원에게 알림이 가고(HousingPolicyChangeNotifier), 삭제된 정책은 관심정책에서도 빠진다.
 * 진단 요청마다 AI 엔진에 실어 보내는 목록(getForEngine)은 5분 캐시하고, 저장/삭제하면 바로 비운다(대출 조건/기준소득과 같은 방식).
 * 알림 실패가 저장을 되돌리지 않도록 이 서비스 메서드 자체는 하나의 큰 트랜잭션으로 묶지 않고 저장소 호출마다 각자 커밋한다.
 */
@Service
@RequiredArgsConstructor
public class HousingPolicyService {

    private final HousingPolicyRepository policyRepository;
    private final FavoritePolicyRepository favoritePolicyRepository;
    private final HousingPolicyChangeNotifier notifier;

    /** 진단 요청마다 읽던 값이라 5분 캐시한다(클라우드 DB 왕복이 느리다). 저장/삭제하면 비운다. */
    private final TtlCache<List<HousingPolicyForEngine>> engineCache = new TtlCache<>(5 * 60 * 1000L);

    /** 관리자 화면용 전체 목록 (id 오름차순 = 리포트 표시 순서) */
    public List<HousingPolicyResponse> list() {
        return policyRepository.findAllByOrderByIdAsc().stream().map(HousingPolicyResponse::from).toList();
    }

    /** AI 엔진에 진단 요청과 함께 실어 보낼 목록 (캐시). 바뀌지 않는 List다. */
    public List<HousingPolicyForEngine> getForEngine() {
        List<HousingPolicyForEngine> policies =
                engineCache.get(() -> policyRepository.findAllByOrderByIdAsc().stream().map(HousingPolicyForEngine::from).toList());
        if (policies.isEmpty()) {
            engineCache.evict(); // 빈 목록(첫 시드 전 등)은 캐시하지 않는다 - 정책이 들어오면 바로 보이게
        }
        return policies;
    }

    public HousingPolicySaveResponse create(HousingPolicyRequest request) {
        N n = N.of(request);
        HousingPolicy saved = policyRepository.saveAndFlush(HousingPolicy.of(n.region, n.agency, n.name, n.description,
                n.minAge, n.maxAge, n.maxAnnualIncome, n.maxAsset, n.medianIncomePercent, n.requireBasicLivelihood,
                n.requireSme, n.requireNewlywed, n.requireNoHousehold, n.loan, n.note, n.link));
        engineCache.evict();
        return new HousingPolicySaveResponse(HousingPolicyResponse.from(saved), 0);
    }

    public HousingPolicySaveResponse update(Long id, HousingPolicyRequest request) {
        HousingPolicy policy = find(id);
        N n = N.of(request);
        // 사용자에게 보이는 항목(관리자용 메모와 대출 구분 제외)이 바뀐 경우에만 알림을 보낸다
        List<String> changed = changedFields(policy, n);

        policy.update(n.region, n.agency, n.name, n.description, n.minAge, n.maxAge, n.maxAnnualIncome, n.maxAsset,
                n.medianIncomePercent, n.requireBasicLivelihood, n.requireSme, n.requireNewlywed, n.requireNoHousehold,
                n.loan, n.note, n.link);
        HousingPolicy saved = policyRepository.saveAndFlush(policy);
        engineCache.evict();

        int notified = changed.isEmpty() ? 0 : notifier.policyChanged(saved, changed, notifier.watchers(saved.getId()));
        return new HousingPolicySaveResponse(HousingPolicyResponse.from(saved), notified);
    }

    public HousingPolicySaveResponse delete(Long id) {
        HousingPolicy policy = find(id);
        List<Long> watchers = notifier.watchers(id); // 지우면 관심정책 행도 사라지므로 먼저 구해 둔다
        favoritePolicyRepository.deleteByPolicyId(id);
        policyRepository.delete(policy);
        policyRepository.flush();
        engineCache.evict();

        int notified = watchers.isEmpty() ? 0 : notifier.policyDeleted(policy, watchers);
        return new HousingPolicySaveResponse(null, notified);
    }

    private HousingPolicy find(Long id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다. 이미 삭제되었을 수 있어요."));
    }

    /** 바뀐 항목 이름 목록 (순서는 관리자 표의 칸 순서) */
    static List<String> changedFields(HousingPolicy p, N n) {
        List<String> changed = new ArrayList<>();
        add(changed, "지역", p.getRegion(), n.region);
        add(changed, "기관명", p.getAgency(), n.agency);
        add(changed, "지원정책명", p.getName(), n.name);
        add(changed, "지원혜택", p.getDescription(), n.description);
        add(changed, "나이 조건", List.of(nz(p.getMinAge()), nz(p.getMaxAge())), List.of(nz(n.minAge), nz(n.maxAge)));
        add(changed, "연소득 조건", p.getMaxAnnualIncome(), n.maxAnnualIncome);
        add(changed, "총자산 조건", p.getMaxAsset(), n.maxAsset);
        add(changed, "기준중위소득 조건", p.getMedianIncomePercent(), n.medianIncomePercent);
        add(changed, "기초수급자 조건", p.isRequireBasicLivelihood(), n.requireBasicLivelihood);
        add(changed, "중소기업 조건", p.isRequireSme(), n.requireSme);
        add(changed, "신혼부부 조건", p.isRequireNewlywed(), n.requireNewlywed);
        add(changed, "무주택 조건", p.isRequireNoHousehold(), n.requireNoHousehold);
        add(changed, "링크", p.getLink(), n.link);
        return changed;
    }

    private static void add(List<String> changed, String label, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changed.add(label);
        }
    }

    private static int nz(Integer v) {
        return v == null ? -1 : v;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 요청 값을 저장 형태로 정리한 것 (문자열은 공백 정리, 빈 링크/메모는 null) */
    record N(String region, String agency, String name, String description, Integer minAge, Integer maxAge,
             Integer maxAnnualIncome, Integer maxAsset, Integer medianIncomePercent, boolean requireBasicLivelihood,
             boolean requireSme, boolean requireNewlywed, boolean requireNoHousehold, boolean loan, String note, String link) {
        static N of(HousingPolicyRequest r) {
            return new N(r.region().trim(), r.agency().trim(), r.name().trim(), r.description().trim(), r.minAge(), r.maxAge(),
                    r.maxAnnualIncome(), r.maxAsset(), r.medianIncomePercent(), r.requireBasicLivelihood(), r.requireSme(),
                    r.requireNewlywed(), r.requireNoHousehold(), r.loan(), trimToNull(r.note()), trimToNull(r.link()));
        }
    }
}
