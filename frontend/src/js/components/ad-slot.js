/**
 * [담당: 송귀성] 공통 UI 컴포넌트 - 광고 자리 (2026-10-08)
 *
 * 페이지 어디든 아래 한 줄을 넣으면 그 자리에 광고가 채워진다 (아직 실제 페이지에는 배치하지 않았다 - 위치는 나중에 정한다).
 *   <div data-ad-slot="home-bottom" data-ad-kind="both" data-ad-size="banner"></div>
 * 이 파일(+ 직접 광고 조건식 ad-targeting.js)을 불러 두면 DOMContentLoaded에 자동으로 채운다. 불러올 스크립트 순서:
 *   config.js(선택) -> auth.js(선택, 로그인 여부) -> address-match-util.js(선택, 직장 구 보정) -> mypage.js(선택) -> ad-targeting.js -> ad-slot.js
 *
 *  - data-ad-slot   자리 이름(영문 소문자·숫자·-·_). 관리자가 배너마다 "노출할 자리"를 이 이름으로 제한할 수 있다(비우면 모든 자리).
 *  - data-ad-kind   direct(직접 배너만) / google(구글 AdSense만) / both(기본: 직접 배너 우선, 맞는 배너가 없으면 구글 광고)
 *  - data-ad-size   banner(가로로 넓게, 기본) / card(작은 카드형)
 *  - data-adsense-slot  이 자리의 AdSense 광고 단위 ID (없으면 config.js의 ADSENSE_SLOT)
 * 직접 광고: 서버 GET /api/banners(활성+기간 안)를 받아 방문자 조건(월세 성향/직장 구/이사 일정/자리)에 맞는 배너 중 무작위 1개를 보인다.
 *   모든 배너에는 "광고" 표시가 붙고, 링크는 새 탭 + rel="sponsored noopener nofollow"로 연다. 노출/클릭은 집계하지 않는다.
 * 구글 광고: config.js에 ADSENSE_CLIENT(게시자 ID, ca-pub-…)와 광고 단위 ID가 있을 때만 AdSense를 불러온다. 없으면 아무것도 그리지 않는다.
 * 보여 줄 광고가 없으면 자리를 접는다(빈 공간을 남기지 않는다).
 */
(function (root) {
  const ORIGIN = ["localhost", "127.0.0.1"].includes(location.hostname) ? "http://localhost:8080" : "https://team3-q05z.onrender.com";
  const BANNERS_API = `${ORIGIN}/api/banners`;
  const STYLE_ID = "ch-ad-slot-style";

  let bannersPromise = null;
  let profilePromise = null;
  let adsenseScriptAdded = false;

  function injectStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = `
      .ch-ad-slot { display: block; margin: 16px auto; width: 100%; }
      .ch-ad-slot[hidden] { display: none !important; }
      .ch-ad-slot[data-ad-size="banner"] { max-width: 970px; }
      .ch-ad-slot[data-ad-size="card"] { max-width: 320px; }
      .ch-ad-link { position: relative; display: block; border-radius: 12px; overflow: hidden; border: 1px solid #e7edf4; background: #fff; line-height: 0; }
      .ch-ad-link img { display: block; width: 100%; height: auto; }
      .ch-ad-tag { position: absolute; top: 8px; left: 8px; line-height: 1; font-size: 11px; font-weight: 700; padding: 3px 7px; border-radius: 999px; background: rgba(15,23,42,.72); color: #fff; }
    `;
    document.head.appendChild(style);
  }

  function readJson(key) {
    try {
      return JSON.parse(localStorage.getItem(key) || "null");
    } catch (e) {
      return null;
    }
  }

  function fetchBanners() {
    if (!bannersPromise) {
      bannersPromise = fetch(BANNERS_API)
        .then((res) => res.json())
        .then((body) => (body && body.success !== false && Array.isArray(body.data) ? body.data : []))
        .catch(() => []);   // 광고를 못 불러와도 페이지는 그대로 (자리만 접힌다)
    }
    return bannersPromise;
  }

  /** 방문자 프로필: 마지막으로 진단에 입력한 조건 > 마이페이지 저장값. 적정 월세 상한은 마지막 진단 결과 > 홈 미리보기 캐시에서 읽는다(없으면 성향 구분 없이 랜덤). */
  function loadProfile() {
    if (!profilePromise) {
      profilePromise = (async () => {
        const T = root.CustomHouseAdTargeting;
        const loggedIn = !!(root.CustomHouseAuthApi && root.CustomHouseAuthApi.isLoggedIn());
        if (!loggedIn) return T.buildProfile({ loggedIn: false });
        const last = readJson("customhouse:lastDiagnosisCondition");
        let saved = null;
        if (root.CustomHouseMypageApi && root.CustomHouseMypageApi.getMyCondition) {
          try {
            saved = await root.CustomHouseMypageApi.getMyCondition();
          } catch (e) {
            saved = null;
          }
        }
        const util = root.CustomHouseAddressMatchUtil;
        const source = last && last.workLocation ? last : saved || {};
        const fixed = util && util.normalizeWorkLocation ? util.normalizeWorkLocation(source) : source;
        const lastDiag = readJson("customhouse:lastListingDiagnosis");
        const preview = readJson("customhouse:homePreview:v2");
        const affordable = (lastDiag && lastDiag.affordable_rent) || (preview && preview.summary && preview.summary.affordable_rent) || null;
        const desiredRent = last && last.desiredRent != null ? last.desiredRent : saved && saved.desiredRent;
        return T.buildProfile({
          loggedIn: true,
          workRegion: fixed && fixed.workLocation,
          moveSchedule: last && last.moveSchedule,
          desiredRent,
          affordableRent: affordable,
        });
      })();
    }
    return profilePromise;
  }

  function imageSrc(url) {
    return typeof url === "string" && url.startsWith("/uploads/") ? `${ORIGIN}${url}` : url;
  }

  function isHttpUrl(url) {
    return typeof url === "string" && /^https?:\/\/\S+$/i.test(url);
  }

  /** 배너 한 장을 자리에 그린다 (DOM API로만 만들어 배너 문구가 HTML로 해석되지 않는다) */
  function renderBanner(el, banner) {
    if (!isHttpUrl(banner.linkUrl)) return false;
    const link = document.createElement("a");
    link.className = "ch-ad-link";
    link.href = banner.linkUrl;
    link.target = "_blank";
    link.rel = "sponsored noopener nofollow";
    const img = document.createElement("img");
    img.src = imageSrc(banner.imageUrl);
    img.alt = banner.altText || banner.title || "광고";
    img.loading = "lazy";
    img.decoding = "async";
    const tag = document.createElement("span");
    tag.className = "ch-ad-tag";
    tag.textContent = "광고";
    link.append(img, tag);
    el.replaceChildren(link);
    return true;
  }

  function ensureAdsenseScript(client) {
    if (adsenseScriptAdded) return;
    adsenseScriptAdded = true;
    const s = document.createElement("script");
    s.async = true;
    s.crossOrigin = "anonymous";
    s.src = `https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${encodeURIComponent(client)}`;
    document.head.appendChild(s);
  }

  /** 구글 AdSense 광고 단위를 자리에 붙인다. 게시자 ID/광고 단위 ID가 없으면 false(아무것도 그리지 않음). */
  function renderGoogle(el) {
    const cfg = root.CUSTOMHOUSE_CONFIG || {};
    const client = cfg.ADSENSE_CLIENT;
    const slotId = el.dataset.adsenseSlot || cfg.ADSENSE_SLOT;
    if (!/^ca-pub-\d+$/.test(client || "") || !/^\d+$/.test(slotId || "")) return false;
    ensureAdsenseScript(client);
    const ins = document.createElement("ins");
    ins.className = "adsbygoogle";
    ins.style.display = "block";
    ins.setAttribute("data-ad-client", client);
    ins.setAttribute("data-ad-slot", slotId);
    ins.setAttribute("data-ad-format", "auto");
    ins.setAttribute("data-full-width-responsive", "true");
    el.replaceChildren(ins);
    try {
      (root.adsbygoogle = root.adsbygoogle || []).push({});
    } catch (e) {
      /* AdSense가 막혀 있어도(광고 차단 등) 페이지는 그대로 */
    }
    return true;
  }

  /**
   * 자리 하나를 채운다.
   * @param {HTMLElement} el data-ad-slot 요소
   * @param {{profile?:object, banners?:object[]}} [options] 미리보기용으로 프로필/배너 목록을 직접 넘길 수 있다
   * @returns {Promise<"direct"|"google"|null>} 무엇을 그렸는지 (없으면 null, 자리는 접힌다)
   */
  async function mount(el, options) {
    const opts = options || {};
    injectStyle();
    const kind = el.dataset.adKind || "both";
    const slot = el.dataset.adSlot || "";
    if (!el.dataset.adSize) el.dataset.adSize = "banner";
    el.classList.add("ch-ad-slot");
    el.hidden = true;   // 채울 광고가 정해질 때까지 빈 공간을 만들지 않는다

    let shown = null;
    if (kind === "direct" || kind === "both") {
      const [banners, profile] = await Promise.all([opts.banners || fetchBanners(), opts.profile || loadProfile()]);
      const banner = root.CustomHouseAdTargeting.pickBanner(banners, profile, slot);
      if (banner && renderBanner(el, banner)) shown = "direct";
    }
    if (!shown && (kind === "google" || kind === "both") && renderGoogle(el)) shown = "google";
    el.hidden = !shown;
    if (!shown) el.replaceChildren();
    return shown;
  }

  function mountAll(container) {
    return Promise.all([...(container || document).querySelectorAll("[data-ad-slot]")].map((el) => mount(el)));
  }

  root.CustomHouseAdSlot = { mount, mountAll, fetchBanners, loadProfile };
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => mountAll());
  } else {
    mountAll();
  }
})(window);
