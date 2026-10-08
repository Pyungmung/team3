// [담당: 송귀성] 직장 구 보정(address-match-util.js normalizeWorkLocation) 단위 테스트 (2026-10-08).
// 실행: node frontend/tests/address-match-util.test.js  (프로젝트 루트에서) - 실패하면 종료 코드 1
const assert = require("assert");
global.window = {};
require("../src/js/utils/address-match-util.js");
const { normalizeWorkLocation } = window.CustomHouseAddressMatchUtil;

// 옛날에 가장 가까운 구청 기준으로 저장된 서초구 주소(관악구) -> 주소의 구로 바로잡는다 (다른 값은 그대로)
const stale = { workLocation: "관악구", workAddress: "서울 서초구 동작대로 132", workLat: 37.488, workLon: 126.983, deposit: 10000 };
const fixed = normalizeWorkLocation(stale);
assert.strictEqual(fixed.workLocation, "서초구");
assert.strictEqual(fixed.deposit, 10000);
assert.strictEqual(stale.workLocation, "관악구", "원본 객체는 바꾸지 않는다");

// 이미 맞으면 같은 객체, 주소가 없거나 구를 못 찾으면 그대로
const ok = { workLocation: "서초구", workAddress: "서울 서초구 서초중앙로 5" };
assert.strictEqual(normalizeWorkLocation(ok), ok);
const noAddr = { workLocation: "강남구" };
assert.strictEqual(normalizeWorkLocation(noAddr), noAddr);
const unknown = { workLocation: "강남구", workAddress: "부산 해운대구 해운대로 1" };
assert.strictEqual(normalizeWorkLocation(unknown), unknown);
assert.strictEqual(normalizeWorkLocation(null), null);

// 분당구(경기 성남시) 주소
assert.strictEqual(normalizeWorkLocation({ workLocation: "강남구", workAddress: "경기 성남시 분당구 판교역로 1" }).workLocation, "분당구");

console.log("address-match-util: 모든 테스트 통과");
