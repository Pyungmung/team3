// [담당: 송귀성] 광고하기 매물 끼워 넣기(src/js/utils/ad-interleave.js) 단위 테스트 (2026-10-08).
// 실행: node frontend/tests/ad-interleave.test.js  (프로젝트 루트에서) - 실패하면 종료 코드 1
const assert = require("assert");
const { interleaveAds } = require("../src/js/utils/ad-interleave.js");

const normals = (n) => Array.from({ length: n }, (_, i) => ({ listing_id: `N${i + 1}` }));
const ads = (n) => Array.from({ length: n }, (_, i) => ({ listing_id: `A${i + 1}` }));
const ids = (entries) => entries.map((e) => e.listing.listing_id);

// 5의 배수 다음 자리에 광고가 들어간다 (#5 다음, #10 다음 ...)
assert.deepStrictEqual(ids(interleaveAds(normals(12), ads(5))),
  ["N1", "N2", "N3", "N4", "N5", "A1", "N6", "N7", "N8", "N9", "N10", "A2", "N11", "N12"]);

// 일반 카드 순번(normalIdx)은 광고 때문에 밀리지 않는다
const entries = interleaveAds(normals(11), ads(3));
assert.deepStrictEqual(entries.filter((e) => !e.isAd).map((e) => e.normalIdx), [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
assert.deepStrictEqual(entries.filter((e) => e.isAd).map((e) => [e.adIdx, e.normalIdx]), [[0, -1], [1, -1]]);

// 정확히 5개면 맨 끝(#5 다음)에 광고가 붙는다
assert.deepStrictEqual(ids(interleaveAds(normals(5), ads(2))), ["N1", "N2", "N3", "N4", "N5", "A1"]);
// 5개 미만이면 끼울 자리가 없다
assert.deepStrictEqual(ids(interleaveAds(normals(4), ads(2))), ["N1", "N2", "N3", "N4"]);

// 광고가 모자라면 있는 만큼만, 광고가 없으면 그대로
assert.deepStrictEqual(ids(interleaveAds(normals(16), ads(1))).filter((x) => x.startsWith("A")), ["A1"]);
assert.deepStrictEqual(ids(interleaveAds(normals(7), [])), ids(interleaveAds(normals(7), null)).slice());
assert.strictEqual(interleaveAds(normals(7), []).length, 7);

// 일반 카드가 없으면 광고도 없다 / 빈 입력 방어
assert.deepStrictEqual(interleaveAds([], ads(3)), []);
assert.deepStrictEqual(interleaveAds(null, null), []);

// 광고가 일반 카드보다 많아도 슬롯 수만큼만 쓴다 (같은 광고를 두 번 쓰지 않는다)
const many = ids(interleaveAds(normals(15), ads(10)));
assert.strictEqual(new Set(many).size, many.length);
assert.strictEqual(many.filter((x) => x.startsWith("A")).length, 3);

console.log("ad-interleave: 모든 테스트 통과");
