// [담당: 송귀성] 광고하기 매물 끼워 넣기(src/js/utils/ad-interleave.js) 단위 테스트 (2026-10-08).
// 실행: node frontend/tests/ad-interleave.test.js  (프로젝트 루트에서) - 실패하면 종료 코드 1
const assert = require("assert");
const { interleaveAds } = require("../src/js/utils/ad-interleave.js");

const normals = (n) => Array.from({ length: n }, (_, i) => ({ listing_id: `N${i + 1}` }));
const ads = (n) => Array.from({ length: n }, (_, i) => ({ listing_id: `A${i + 1}` }));
const ids = (entries) => entries.map((e) => e.listing.listing_id);
const adIds = (entries) => entries.filter((e) => e.isAd).map((e) => e.listing.listing_id);

// 결정적 난수 (선형합동) - 테스트를 재현 가능하게
function seeded(seed) {
  let s = seed;
  return () => {
    s = (s * 1664525 + 1013904223) % 4294967296;
    return s / 4294967296;
  };
}

// 5의 배수 다음 자리마다 광고가 하나씩 들어간다 (#5 다음, #10 다음 ...) - 광고가 충분하면 전부 서로 다른 광고
const a = interleaveAds(normals(12), ads(5), 5, seeded(1));
assert.deepStrictEqual(ids(a).filter((x) => x.startsWith("N")), normals(12).map((n) => n.listing_id), "일반 카드 순서는 그대로");
assert.strictEqual(a[5].isAd, true);   // N1..N5 다음
assert.strictEqual(a[11].isAd, true);  // N6..N10 다음
assert.strictEqual(a.filter((e) => e.isAd).length, 2);
assert.strictEqual(new Set(adIds(a)).size, 2);

// 일반 카드 순번(normalIdx)은 광고 때문에 밀리지 않는다
const entries = interleaveAds(normals(11), ads(3), 5, seeded(2));
assert.deepStrictEqual(entries.filter((e) => !e.isAd).map((e) => e.normalIdx), [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
assert.ok(entries.filter((e) => e.isAd).every((e) => e.normalIdx === -1 && e.adIdx >= 0));

// 정확히 5개면 맨 끝(#5 다음)에 광고가 붙고, 5개 미만이면 끼울 자리가 없다
assert.strictEqual(interleaveAds(normals(5), ads(2), 5, seeded(3)).length, 6);
assert.strictEqual(interleaveAds(normals(4), ads(2), 5, seeded(3)).length, 4);

// 광고가 자리보다 모자라면 풀을 다시 섞어 반복해서 올린다 - 마지막 자리까지 항상 광고가 있다
const many = interleaveAds(normals(100), ads(3), 5, seeded(4));   // 광고 자리 20개, 광고는 3개
const placed = many.filter((e) => e.isAd);
assert.strictEqual(placed.length, 20);
for (let slot = 0; slot < 20; slot++) assert.strictEqual(many[slot * 6 + 5].isAd, true, `${slot + 1}번째 자리`);
// 한 바퀴(3개)마다 풀의 모든 광고가 한 번씩 나온다
for (let k = 0; k + 3 <= placed.length; k += 3) {
  assert.strictEqual(new Set(placed.slice(k, k + 3).map((e) => e.adIdx)).size, 3, `${k / 3 + 1}번째 바퀴`);
}
// 같은 광고가 연달아 나오지 않는다 (광고가 2개 이상일 때)
for (const n of [2, 3, 4, 7]) {
  for (let seed = 1; seed <= 25; seed++) {
    const p = interleaveAds(normals(200), ads(n), 5, seeded(seed)).filter((e) => e.isAd);
    for (let k = 1; k < p.length; k++) assert.notStrictEqual(p[k].adIdx, p[k - 1].adIdx, `n=${n} seed=${seed} k=${k}`);
  }
}
// 광고가 1개뿐이면 어쩔 수 없이 같은 광고가 반복된다
assert.deepStrictEqual(adIds(interleaveAds(normals(15), ads(1), 5, seeded(5))), ["A1", "A1", "A1"]);

// 광고가 자리보다 많으면 중복 없이 자리 수만큼만 쓴다
const fewSlots = adIds(interleaveAds(normals(15), ads(10), 5, seeded(6)));
assert.strictEqual(fewSlots.length, 3);
assert.strictEqual(new Set(fewSlots).size, 3);

// 광고 풀이 같아도 조회할 때마다(난수가 달라지면) 노출 순서가 달라진다
const orders = new Set();
for (let seed = 1; seed <= 20; seed++) orders.add(adIds(interleaveAds(normals(30), ads(6), 5, seeded(seed))).join(","));
assert.ok(orders.size > 5, "무작위 순서");

// 광고가 없거나 일반 카드가 없는 경우 / 빈 입력 방어
assert.strictEqual(interleaveAds(normals(7), [], 5, seeded(7)).length, 7);
assert.strictEqual(interleaveAds(normals(7), null).length, 7);
assert.deepStrictEqual(interleaveAds([], ads(3), 5, seeded(8)), []);
assert.deepStrictEqual(interleaveAds(null, null), []);

// 기본 난수(Math.random)로도 동작한다
assert.strictEqual(interleaveAds(normals(50), ads(4)).filter((e) => e.isAd).length, 10);

console.log("ad-interleave: 모든 테스트 통과");
