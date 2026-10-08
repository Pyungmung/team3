// [담당: 송귀성] 직접 배너 광고 조건식(src/js/utils/ad-targeting.js) 단위 테스트 (2026-10-08).
// 실행: node frontend/tests/ad-targeting.test.js  (프로젝트 루트에서) - 실패하면 종료 코드 1
const assert = require("assert");
const { viewerSegment, buildProfile, matchBanner, pickBanner } = require("../src/js/utils/ad-targeting.js");

// ---- 월세 성향: 최대 월세 vs 적정 월세 상한 ----
assert.strictEqual(viewerSegment({ desiredRent: 40, affordableRent: 46 }), "SAVING");
assert.strictEqual(viewerSegment({ desiredRent: 46, affordableRent: 46 }), "SAVING", "같으면 절약형");
assert.strictEqual(viewerSegment({ desiredRent: 46.1, affordableRent: 46 }), "PREMIUM");
assert.strictEqual(viewerSegment({ desiredRent: 80, affordableRent: 46 }), "PREMIUM");
// 최대 월세를 비웠거나 상한을 모르면 성향 구분 없음(null)
for (const v of [null, undefined, "", 0, -5, "abc"]) assert.strictEqual(viewerSegment({ desiredRent: v, affordableRent: 46 }), null, `desired=${v}`);
for (const v of [null, undefined, "", 0, NaN]) assert.strictEqual(viewerSegment({ desiredRent: 50, affordableRent: v }), null, `affordable=${v}`);
assert.strictEqual(viewerSegment({ desiredRent: "50", affordableRent: "46.0" }), "PREMIUM", "문자열 숫자도 허용");
assert.strictEqual(viewerSegment(null), null);

// ---- 방문자 프로필 ----
const guest = buildProfile({ loggedIn: false, desiredRent: 50, affordableRent: 46, workRegion: "서초구" });
assert.deepStrictEqual(guest, { loggedIn: false, workRegion: null, moveSchedule: null, desiredRent: null, affordableRent: null, segment: null });
const member = buildProfile({ loggedIn: true, desiredRent: 50, affordableRent: 46, workRegion: "서초구", moveSchedule: "WITHIN_3M" });
assert.strictEqual(member.segment, "PREMIUM");
assert.strictEqual(member.workRegion, "서초구");
assert.strictEqual(buildProfile({ loggedIn: true }).segment, null);

// ---- 배너 매칭 ----
const b = (over) => ({ id: 1, segment: "ALL", regions: [], moveWithin: "ANY", slots: [], ...over });
const saving = buildProfile({ loggedIn: true, desiredRent: 40, affordableRent: 46 });
const premium = buildProfile({ loggedIn: true, desiredRent: 70, affordableRent: 46 });
const unknown = buildProfile({ loggedIn: true, desiredRent: null, affordableRent: 46 });

// 성향
assert.ok(matchBanner(b({ segment: "ALL" }), saving, "s") && matchBanner(b({ segment: "ALL" }), premium, "s"));
assert.ok(matchBanner(b({ segment: "SAVING" }), saving, "s"));
assert.ok(!matchBanner(b({ segment: "SAVING" }), premium, "s"));
assert.ok(matchBanner(b({ segment: "PREMIUM" }), premium, "s"));
assert.ok(!matchBanner(b({ segment: "PREMIUM" }), saving, "s"));
// 최대 월세를 비웠거나 비로그인이면 성향 구분 없이 모든 배너가 후보
for (const p of [unknown, guest]) {
  assert.ok(matchBanner(b({ segment: "SAVING" }), p, "s") && matchBanner(b({ segment: "PREMIUM" }), p, "s") && matchBanner(b({ segment: "ALL" }), p, "s"));
}

// 직장 자치구: 비우면 제한 없음, 있으면 방문자 직장 구가 있어야 한다(모르면 제외)
assert.ok(matchBanner(b({ regions: [] }), guest, "s"));
assert.ok(matchBanner(b({ regions: ["서초구", "강남구"] }), buildProfile({ loggedIn: true, workRegion: "강남구" }), "s"));
assert.ok(!matchBanner(b({ regions: ["서초구"] }), buildProfile({ loggedIn: true, workRegion: "관악구" }), "s"));
assert.ok(!matchBanner(b({ regions: ["서초구"] }), guest, "s"));
assert.ok(!matchBanner(b({ regions: ["서초구"] }), buildProfile({ loggedIn: true }), "s"));

// 이사 일정
const move = (m) => buildProfile({ loggedIn: true, moveSchedule: m });
assert.ok(matchBanner(b({ moveWithin: "ANY" }), guest, "s"));
assert.ok(matchBanner(b({ moveWithin: "IMMEDIATE" }), move("IMMEDIATE"), "s"));
assert.ok(!matchBanner(b({ moveWithin: "IMMEDIATE" }), move("WITHIN_3M"), "s"));
assert.ok(matchBanner(b({ moveWithin: "WITHIN_3M" }), move("IMMEDIATE"), "s") && matchBanner(b({ moveWithin: "WITHIN_3M" }), move("WITHIN_3M"), "s"));
assert.ok(!matchBanner(b({ moveWithin: "WITHIN_3M" }), move("WITHIN_6M"), "s"));
assert.ok(!matchBanner(b({ moveWithin: "WITHIN_3M" }), move("EXPLORING"), "s"));
assert.ok(!matchBanner(b({ moveWithin: "IMMEDIATE" }), guest, "s"), "이사 일정을 모르면 일정 조건 배너는 제외");

// 광고 자리 이름: 비우면 모든 자리, 있으면 그 자리에서만
assert.ok(matchBanner(b({ slots: [] }), guest, "home-bottom") && matchBanner(b({ slots: [] }), guest, undefined));
assert.ok(matchBanner(b({ slots: ["home-bottom", "report-side"] }), guest, "report-side"));
assert.ok(!matchBanner(b({ slots: ["home-bottom"] }), guest, "report-side"));
assert.ok(!matchBanner(b({ slots: ["home-bottom"] }), guest, undefined));

// ---- 무작위 선택 ----
const pool = [b({ id: 1, segment: "SAVING" }), b({ id: 2, segment: "PREMIUM" }), b({ id: 3, segment: "ALL" }), b({ id: 4, regions: ["서초구"] })];
const idsFor = (profile) => {
  const seen = new Set();
  for (let k = 0; k < 40; k++) seen.add(pickBanner(pool, profile, "s", () => k / 40).id);
  return [...seen].sort();
};
assert.deepStrictEqual(idsFor(saving), [1, 3], "절약형은 절약형+전체");
assert.deepStrictEqual(idsFor(premium), [2, 3], "프리미엄형은 프리미엄형+전체");
assert.deepStrictEqual(idsFor(unknown), [1, 2, 3], "최대 월세를 비우면 성향 구분 없이 랜덤(자치구 조건 배너는 직장 구를 모르니 제외)");
assert.deepStrictEqual(idsFor(buildProfile({ loggedIn: true, workRegion: "서초구" })), [1, 2, 3, 4]);
assert.strictEqual(pickBanner([], saving, "s"), null);
assert.strictEqual(pickBanner(null, saving, "s"), null);
assert.strictEqual(pickBanner([b({ regions: ["서초구"] })], guest, "s"), null);
assert.ok(pickBanner(pool, saving, "s", () => 0.999999).id, "rng가 1에 가까워도 범위 안");
// 기본 난수로도 동작
assert.ok([1, 3].includes(pickBanner(pool, saving, "s").id));

console.log("ad-targeting: 모든 테스트 통과");
