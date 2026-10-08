/**
 * [담당: 송귀성] 광고하기 매물 끼워 넣기 (2026-10-08)
 * 추천 목록(일반 카드)의 5·10·15… 번째 바로 다음 자리에 광고 카드를 하나씩 끼워 넣는다. 일반 카드의 순번(#N)은 그대로고,
 * 광고 카드는 순번 대신 "광고하기 매물" 배지를 달기 때문에 이 함수는 순번을 새로 매기지 않는다 - 각 항목에 원래 위치만 실어 준다.
 * 광고가 모자라면 그만큼만 끼우고(남는 자리는 비운다), 광고가 없으면 일반 카드 그대로다. 일반 카드가 없으면 끼울 자리가 없어 광고도 없다.
 *
 * 브라우저에서는 window.CustomHouseAdInterleave, 노드 테스트(frontend/tests/ad-interleave.test.js)에서는 require로 쓴다.
 */
(function (root) {
  const AD_EVERY = 5;

  /**
   * @param {object[]} normals 일반 추천 카드(순위순)
   * @param {object[]} ads 광고 카드(서버가 이미 무작위로 섞어 줌)
   * @returns {{listing:object, isAd:boolean, normalIdx:number, adIdx:number}[]} 표시 순서의 항목. 광고는 normalIdx=-1, 일반은 adIdx=-1.
   */
  function interleaveAds(normals, ads, every) {
    const step = every > 0 ? every : AD_EVERY;
    const entries = [];
    let adPos = 0;
    (normals || []).forEach((listing, i) => {
      entries.push({ listing, isAd: false, normalIdx: i, adIdx: -1 });
      if ((i + 1) % step === 0 && ads && adPos < ads.length) {
        entries.push({ listing: ads[adPos], isAd: true, normalIdx: -1, adIdx: adPos });
        adPos += 1;
      }
    });
    return entries;
  }

  const api = { interleaveAds, AD_EVERY };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  if (root) root.CustomHouseAdInterleave = api;
})(typeof window !== "undefined" ? window : null);
