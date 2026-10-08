/**
 * [담당: 송귀성] 광고하기 매물 끼워 넣기 (2026-10-08)
 * 추천 목록(일반 카드)의 5·10·15… 번째 바로 다음 자리마다 광고 카드를 하나씩 끼워 넣는다. 일반 카드의 순번(#N)은 그대로고,
 * 광고 카드는 순번 대신 "광고하기 매물" 배지를 달기 때문에 이 함수는 순번을 새로 매기지 않는다 - 각 항목에 원래 위치만 실어 준다.
 *
 * 광고 고르는 방식 (2026-10-08 변경): 자리마다 광고 풀에서 무작위로 뽑는다. 풀을 한 바퀴 다 쓰면(광고가 자리 수보다 적을 때) 풀을 다시
 * 섞어서 이미 나온 광고도 반복해 올린다 - 그래서 목록 끝까지(스크롤로 늘어날 때마다) 5의 배수 다음 자리에는 항상 광고가 있다. 같은 광고가
 * 연달아 나오지 않게 새로 섞은 첫 광고가 직전 광고와 같으면 순서를 바꾼다(광고가 1개뿐이면 어쩔 수 없이 반복). 조회할 때마다 무작위다.
 * 광고가 없으면 일반 카드 그대로고, 일반 카드가 없으면 끼울 자리가 없어 광고도 없다.
 *
 * 브라우저에서는 window.CustomHouseAdInterleave, 노드 테스트(frontend/tests/ad-interleave.test.js)에서는 require로 쓴다.
 */
(function (root) {
  const AD_EVERY = 5;

  /** 0..n-1 을 무작위로 섞은 새 배열 (Fisher-Yates) */
  function shuffledIndexes(n, rng) {
    const idx = Array.from({ length: n }, (_, i) => i);
    for (let i = n - 1; i > 0; i--) {
      const j = Math.floor(rng() * (i + 1));
      [idx[i], idx[j]] = [idx[j], idx[i]];
    }
    return idx;
  }

  /**
   * @param {object[]} normals 일반 추천 카드(순위순)
   * @param {object[]} ads 광고 카드(풀) - 서버가 보낸 순서와 상관없이 여기서 다시 무작위로 뽑는다
   * @param {number} [every] 몇 개마다 광고를 끼울지 (기본 5)
   * @param {() => number} [rng] 0 이상 1 미만 난수 함수 (테스트용으로 바꿀 수 있다)
   * @returns {{listing:object, isAd:boolean, normalIdx:number, adIdx:number}[]} 표시 순서의 항목. 광고는 normalIdx=-1, 일반은 adIdx=-1.
   *   adIdx는 ads 배열 안의 위치다(같은 광고가 여러 번 나오면 같은 adIdx가 반복된다).
   */
  function interleaveAds(normals, ads, every, rng) {
    const step = every > 0 ? every : AD_EVERY;
    const random = typeof rng === "function" ? rng : Math.random;
    const pool = ads || [];
    const entries = [];
    let bag = [];
    let lastAd = -1;
    (normals || []).forEach((listing, i) => {
      entries.push({ listing, isAd: false, normalIdx: i, adIdx: -1 });
      if ((i + 1) % step !== 0 || pool.length === 0) return;
      if (bag.length === 0) {
        bag = shuffledIndexes(pool.length, random);
        if (bag.length > 1 && bag[0] === lastAd) {
          const k = 1 + Math.floor(random() * (bag.length - 1));
          [bag[0], bag[k]] = [bag[k], bag[0]];
        }
      }
      lastAd = bag.shift();
      entries.push({ listing: pool[lastAd], isAd: true, normalIdx: -1, adIdx: lastAd });
    });
    return entries;
  }

  const api = { interleaveAds, AD_EVERY };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  if (root) root.CustomHouseAdInterleave = api;
})(typeof window !== "undefined" ? window : null);
