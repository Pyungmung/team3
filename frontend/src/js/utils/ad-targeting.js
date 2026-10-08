/**
 * [담당: 송귀성] 직접 배너 광고 - 방문자에게 어떤 배너를 보일지 정하는 조건식 (2026-10-08)
 *
 * 월세 성향(핵심 조건): 방문자가 입력한 "최대 매물 월세"를 "적정 월세 상한"(소득 x 수도권 RIR, AI 엔진이 계산)과 비교한다.
 *   - 최대 월세 <= 적정 월세 상한  -> SAVING(절약형: 최저가가 강점인 서비스)
 *   - 최대 월세 >  적정 월세 상한  -> PREMIUM(프리미엄형: 프리미엄 서비스)
 *   - 최대 월세를 비웠거나 상한을 모르면(비로그인 등) null = 성향 구분 없음 -> 월세 성향 조건 없이 다른 조건에 맞는 모든 배너가 후보(랜덤)
 * 그 밖의 조건(비워 두면 제한 없음): 직장 자치구, 이사 일정(1개월 이내 / 3개월 이내까지), 광고 자리 이름.
 * 같은 값(최대 월세 == 상한)은 절약형이다.
 *
 * 브라우저에서는 window.CustomHouseAdTargeting, 노드 테스트(frontend/tests/ad-targeting.test.js)에서는 require로 쓴다.
 */
(function (root) {
  const SEGMENT_SAVING = "SAVING";
  const SEGMENT_PREMIUM = "PREMIUM";

  function toPositiveNumber(v) {
    if (v === null || v === undefined || v === "") return null;
    const n = Number(v);
    return Number.isFinite(n) && n > 0 ? n : null;
  }

  /**
   * @param {{desiredRent?:number|string|null, affordableRent?:number|string|null}} p
   * @returns {"SAVING"|"PREMIUM"|null} 최대 월세가 비었거나(0 이하 포함) 적정 월세 상한을 모르면 null
   */
  function viewerSegment(p) {
    const desired = toPositiveNumber(p && p.desiredRent);
    const affordable = toPositiveNumber(p && p.affordableRent);
    if (desired === null || affordable === null) return null;
    return desired <= affordable ? SEGMENT_SAVING : SEGMENT_PREMIUM;
  }

  /** 저장값에서 방문자 프로필을 만든다. 값이 없으면 null(모름)로 둔다. 로그인하지 않았으면 모든 값이 null이다. */
  function buildProfile(input) {
    const i = input || {};
    if (!i.loggedIn) {
      return { loggedIn: false, workRegion: null, moveSchedule: null, desiredRent: null, affordableRent: null, segment: null };
    }
    const desiredRent = toPositiveNumber(i.desiredRent);
    const affordableRent = toPositiveNumber(i.affordableRent);
    return {
      loggedIn: true,
      workRegion: i.workRegion || null,
      moveSchedule: i.moveSchedule || null,
      desiredRent,
      affordableRent,
      segment: viewerSegment({ desiredRent, affordableRent }),
    };
  }

  function moveMatches(moveWithin, moveSchedule) {
    if (!moveWithin || moveWithin === "ANY") return true;
    if (moveWithin === "IMMEDIATE") return moveSchedule === "IMMEDIATE";
    if (moveWithin === "WITHIN_3M") return moveSchedule === "IMMEDIATE" || moveSchedule === "WITHIN_3M";
    return false;
  }

  /**
   * 이 방문자에게 이 자리에서 이 배너를 보여도 되는가.
   * @param {object} banner 서버 배너 {segment, regions[], moveWithin, slots[]}
   * @param {object} profile buildProfile 결과
   * @param {string} [slot] 광고 자리 이름
   */
  function matchBanner(banner, profile, slot) {
    const p = profile || {};
    const slots = banner.slots || [];
    if (slots.length > 0 && !(slot && slots.includes(slot))) return false;
    const segment = banner.segment || "ALL";
    if (segment !== "ALL" && p.segment && p.segment !== segment) return false;   // 성향을 모르면(null) 성향 조건은 통과
    const regions = banner.regions || [];
    if (regions.length > 0 && !(p.workRegion && regions.includes(p.workRegion))) return false;
    return moveMatches(banner.moveWithin, p.moveSchedule);
  }

  /** 맞는 배너 중 무작위 1개 (없으면 null). rng는 테스트용으로 바꿀 수 있다. */
  function pickBanner(banners, profile, slot, rng) {
    const candidates = (banners || []).filter((b) => matchBanner(b, profile, slot));
    if (candidates.length === 0) return null;
    const random = typeof rng === "function" ? rng : Math.random;
    return candidates[Math.min(candidates.length - 1, Math.floor(random() * candidates.length))];
  }

  const api = { viewerSegment, buildProfile, matchBanner, pickBanner, SEGMENT_SAVING, SEGMENT_PREMIUM };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  if (root) root.CustomHouseAdTargeting = api;
})(typeof window !== "undefined" ? window : null);
