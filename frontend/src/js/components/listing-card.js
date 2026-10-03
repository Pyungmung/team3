/**
 * [담당: 송귀성] 추천 매물 카드 - 리포트(report-listings.html)와 마이페이지 관심 매물(watchlist/list.html)이 같이 쓰는 카드 본문.
 * 관심 매물로 담을 때 리포트 카드의 매물 정보를 통째로 저장해 두고, 마이페이지에서 이 함수로 리포트와 같은 카드를 다시 그린다.
 * 스타일은 css/listing-card.css, 버튼/신고 표시 같은 페이지별 부분은 각 페이지가 카드 본문 앞뒤에 붙인다.
 * 매물 설명/중개사 문구는 사용자가 직접 입력할 수 있으니 화면에 넣기 전에 항상 esc()로 이스케이프한다.
 */
(function () {
  const PHOTO_PLACEHOLDER = "PHOTO_PLACEHOLDER";
  // 통근시간 옆에 보여줄 교통수단 라벨. 리포트 화면은 opts.transportLabel로 직접 넘겨주고(condition.transportType),
  // 마이페이지 관심 매물처럼 그 값이 없으면 즐겨찾기에 같이 저장해둔 r._transport_type(diagnosis/report-listings.html의
  // toggleFavorite 참고)으로 대신 찾는다 - 둘 다 없으면(옛 저장값 등) 교통수단 표시는 생략한다.
  const TRANSPORT_TYPE_LABELS = { CAR: "자동차", WALK: "도보", PUBLIC: "대중교통" };
  // 지도 핀 클릭(scrollToBuildingCard)이 목표 카드까지 전부 렌더링하면서(loadAll) 그 배치만큼 실거래가 +
  // 통근 정확값을 한꺼번에 자동 조회해 과부하가 났다(2026-10-01, 예: 1000번째 매물 핀 클릭 시 999개 카드가
  // 한 틱에 렌더링되며 두 API를 1000번 가까이씩 부름) - 추천 30위 밖은 둘 다 자동 조회를 끈다.
  // 실거래가는 버튼을 눌러야 조회되고(renderReferencePlaceholder), 통근 정확값은 data-commute-listing-id
  // 자체를 안 붙여서(renderBody) wireCommuteAutoLoad가 아예 건너뛴다 - 대신 추천 응답의 직선거리 추정치가
  // 그대로 보인다(별도 조회 버튼은 없음, 추정치 자체가 이미 유효한 표시라 필요 없다고 판단).
  const AUTO_REF_RANK_LIMIT = 30;

  function esc(value) {
    return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
  }

  // 만원 단위 금액을 읽기 쉽게: 150000 -> "15억", 14500 -> "1억 4,500만원", 500 -> "500만원"
  function fmtMoney(man) {
    const n = Number(man || 0);
    if (n >= 10000) {
      const eok = Math.floor(n / 10000);
      const rest = n % 10000;
      return rest ? `${eok}억 ${rest.toLocaleString()}만원` : `${eok}억`;
    }
    return `${n.toLocaleString()}만원`;
  }

  // 만원 단위 비용: 소수 첫째 자리까지, 정수면 소수점 생략 (33.8 / 42)
  function fmtNum(v) {
    const n = Number(v ?? 0);
    return (Math.round(n * 10) / 10).toLocaleString(undefined, { maximumFractionDigits: 1 });
  }

  // 이율(연 %)은 소수 둘째 자리까지: 6.35
  function fmtRate(v) {
    return Number(v ?? 0).toLocaleString(undefined, { maximumFractionDigits: 2 });
  }

  // 기본금리 -> 우대사항별 차감 -> 최종 금리 행들 (보증금 대출 금리 산출 근거 - 두 대출 박스가 같이 쓴다)
  function _rateRowsHtml(l) {
    const items = l.discount_items || [];
    return `<div class="why-key">
      <div class="why-row"><span>기본금리${l.base_rate_kind === "fixed" ? "(보증금 금리)" : l.is_temporary_rate ? "(기준금리 적용)" : "(소득/보증금 산정)"}</span><b>연 ${fmtRate(l.base_rate_percent)}%</b></div>
      ${items.length
        ? items.map((i) => `
      <div class="why-row${i.applied ? " applied" : " dim"}"><span>${i.applied ? "✔ " : ""}${esc(i.label)}${i.count ? ` (${i.count}명)` : ""}${i.applied ? "" : " <em>중복 불가 · 가장 큰 1개만 적용</em>"}</span><b>−${fmtRate(i.discount_percent)}%p</b></div>`).join("")
        : `<div class="why-row soft"><span>해당하는 우대금리 없음</span><b>−0%p</b></div>`}
      ${l.discount_capped ? `<div class="why-note">우대금리 차감 상한 ${fmtRate(l.discount_cap_percent)}%p 적용 → 실제 −${fmtRate(l.discount_percent)}%p</div>` : ""}
      ${l.rate_floor_applied ? `<div class="why-note">우대금리 적용 후 1.0% 미만이라 최종금리 하한 연 1.0%를 적용했어요</div>` : ""}
      <div class="why-row total"><span>최종 금리</span><b>연 ${fmtRate(l.rate_percent)}%</b></div>
    </div>`;
  }

  // 비용 박스의 부가 설명을 항목마다 한 줄씩 금액과 함께 보여준다 (모든 항목 앞에 "+ ", note는 부호 없는 보충 줄).
  function _subLines(terms, notes, flushNotes) {
    const rows = terms.filter(Boolean).map((t) => `<div><span class="sub-plus">+</span>${t}</div>`);
    // 부호가 없는 보충 줄은 "+" 뒤 글자 위치에 맞춰 들여쓴다 (sub-plus 너비 = sub-note 들여쓰기)
    (notes || []).filter(Boolean).forEach((n) => rows.push(`<div class="sub-note">${n}</div>`));
    // flushNotes: 들여쓰기 없이 왼쪽 끝에 붙는 별도 줄
    (flushNotes || []).filter(Boolean).forEach((n) => rows.push(`<div>${n}</div>`));
    return rows.join("");
  }

  // 2026-10-03: 대출 박스 안의 "금리 산출 근거 / 우대 한도 재반영" 펼침 영역 - 기본금리에서 어떤 우대사항으로
  // 얼마를 깎았는지(여러 개에 해당해도 가장 큰 하나만 적용), 공통 한도가 어떤 우대사항으로 바뀌었는지 하나씩 보여준다.
  function _loanWhyHtml(l) {
    const refl = l.limit_reflections || [];
    // 보증금 대출이 필요 없어도(보유 보증금 충분) 월세대출 구조가 있는 대출은 "대출이 필요해지면 적용될 금리"를 같이 보여준다.
    const showRate = l.loan_principal > 0 || l.rent_loan_total_cap_manwon != null;
    if (!showRate && !refl.length) return "";
    const unitNum = (v, unit) => `${fmtNum(v)}${unit === "%" || unit === "㎡" ? unit : "만원"}`;
    const rateSection = !showRate ? "" : `
      <div class="why-title">금리 산출 근거</div>
      ${l.loan_principal > 0 ? "" : `<div class="why-note soft">보유 보증금이 충분해 지금은 보증금 대출이 필요 없어요.<br />아래는 대출이 필요할 때 적용되는 금리예요.</div>`}
      ${_rateRowsHtml(l)}`;
    const reflSection = !refl.length ? "" : `
      <div class="why-title">우대 한도 재반영</div>
      <div class="why-key">${refl.map((x) => `
      <div class="why-row"><span>${esc(x.label)}${x.sources && x.sources.length ? ` <em>${esc(x.sources.join(", "))}</em>` : ""}</span><b>${unitNum(x.base, x.unit)} → ${unitNum(x.effective, x.unit)}</b></div>`).join("")}</div>`;
    return `<details class="loan-why"><summary>예상 금리·한도 근거 보기</summary>${rateSection}${reflSection}</details>`;
  }

  // 2026-10-03: 청년전용 보증부월세대출 "월세 대출 시" 박스의 근거 - 월세 중 얼마가 대출로 충당되고, 무이자 기준액을
  // 넘는 금액에만 얼마의 금리가 붙어 2년 총 이자가 나왔는지 단계별로 보여준다.
  function _rentLoanWhyHtml(l, r) {
    const free = l.rent_loan_free_threshold_manwon;
    const rate = l.rent_loan_rate_percent;
    if (free == null || rate == null) return "";
    const excess = Math.max(0, Number(l.rent_loan_amount_manwon || 0) - free);
    return `<details class="loan-why"><summary>예상 금리·한도 근거 보기</summary>
      <div class="why-title">보증금 대출 산정 근거</div>
      ${l.loan_principal > 0
        ? `<div class="why-row"><span>대출로 메우는 보증금 부족분<br /><em>매물 보증금 ${fmtNum(r.listing_deposit)}만 − 보유 보증금</em></span><b>${fmtNum(l.loan_principal)}만</b></div>
      ${_rateRowsHtml(l)}
      <div class="why-row"><span>월 이자 (부족분 × 연 ${fmtRate(l.rate_percent)}% ÷ 12)</span><b>${fmtNum(l.monthly_interest)}만/월</b></div>`
        : `<div class="why-note soft">보유 보증금이 충분해 보증금 대출은 필요 없어요 (이자 0원)</div>`}
      <div class="why-title">월세대출 산정 근거</div>
      <div class="why-row"><span>월세 ${fmtNum(r.listing_monthly_rent)}만 전액을 월세대출로 충당</span><b>${fmtNum(l.rent_loan_amount_manwon)}만/월</b></div>
      <div class="why-row"><span>이자 붙는 금액 (대출액 − 무이자 기준액)</span><b>${fmtNum(excess)}만/월</b></div>
      <div class="why-row"><span>${fmtNum(free)}만 초과분 매달 누적 총 이자 (24개월기준)</span><b>${Number(l.rent_loan_total_interest || 0).toLocaleString()}원 (월 평균 ${Number(l.rent_loan_monthly_interest || 0).toLocaleString()}원)</b></div>
      <div class="why-row soft" style="margin-top:4px"><span>무이자 기준액</span><b>${fmtNum(free)}만/월</b></div>
      <div class="why-row soft"><span>기준액 초과분 금리</span><b>연 ${fmtRate(rate)}%</b></div>
      <div class="why-row soft"><span>대출 기간 중 최대 월세대출액 ${fmtNum(l.rent_loan_total_cap_manwon)}만원 제한</span></div>
    </details>`;
  }

  // 실거래 참고 1건의 본문 (renderReferencePlaceholder가 만든 <details>를 펼칠 때 wireReferenceLazyLoad가 채운다).
  function _referenceItemHtml(ref) {
    const isJeonse = (ref.monthly_rent || 0) === 0;
    const price = isJeonse
      ? `전세 ${fmtMoney(ref.deposit)}`
      : `보증금 ${fmtMoney(ref.deposit)} / 월세 ${ref.monthly_rent}만원`;
    const renewal =
      ref.contract_type === "갱신" && (ref.pre_deposit || ref.pre_monthly_rent)
        ? `<div>종전 계약: 보증금 ${fmtMoney(ref.pre_deposit)} / 월세 ${ref.pre_monthly_rent ?? 0}만원 · 갱신요구권 ${ref.use_rr_right === "Y" ? "사용" : "미사용"}</div>`
        : "";
    return `
      <div class="pt-1 mt-1 border-t border-gray-100 first:border-0 first:pt-0 first:mt-0">
        <div><b>${esc(ref.contract_date)}</b> ${esc(ref.contract_type)} · ${price}</div>
        <div>${ref.area ? `${ref.area}㎡` : ""}${ref.floor ? ` · ${esc(ref.floor)}층` : ""}${ref.contract_term ? ` · 계약기간 ${esc(ref.contract_term)}` : ""}</div>
        ${renewal}
      </div>`;
  }

  // 실거래가는 카드 맨 아래 "실거래 참고"에 이전 실거래 내역처럼 보조로만 보여준다. 더미 CSV 고정값이
  // 아니라 국토부 API를 실시간 조회하되, 펼쳐야 보이던 것과 달리(2026-10-01) 카드가 뜨자마자 바로
  // 보이도록 처음부터 펼쳐둔다(open) - wireReferenceAutoLoad가 렌더 직후 바로 채운다.
  // autoLoad=false(추천 30위 밖)면 펼쳐두지 않고, 눌러야 조회되는 버튼만 보여준다 - wireReferenceAutoLoad의
  // 위임 클릭 핸들러가 data-ref-manual-btn을 받는다.
  function renderReferencePlaceholder(listingId, autoLoad) {
    if (!listingId) return "";
    if (!autoLoad) {
      return `
        <details class="listing-details" data-ref-manual-listing-id="${esc(listingId)}">
          <summary>📊 실거래 참고 (국토부 · 이 건물의 최근 계약, 최근 2년)</summary>
          <div class="body" data-ref-body>
            <button type="button" class="underline text-gray-600" data-ref-manual-btn>실거래 조회하기</button>
          </div>
        </details>`;
    }
    return `
      <details class="listing-details" open data-ref-listing-id="${esc(listingId)}">
        <summary>📊 실거래 참고 (국토부 · 이 건물의 최근 계약, 최근 2년)</summary>
        <div class="body" data-ref-body>
          <div class="text-gray-400">불러오는 중…</div>
        </div>
      </details>`;
  }

  async function _loadReference(el) {
    const body = el.querySelector("[data-ref-body]");
    const summary = el.querySelector("summary");
    try {
      const result = await CustomHouseWatchlistApi.getListingReference(el.dataset.refListingId);
      const list = result?.transactions || [];
      const scope = result?.scope || "none";
      if (scope === "neighborhood" && summary) {
        summary.textContent = "📊 실거래 참고 (국토부 · 같은 동 최근 계약, 최근 2년)";
      }
      const scopeNote =
        scope === "neighborhood"
          ? `<div class="text-gray-400 mt-1">이 매물은 지번 정보가 없는 유형이라, 건물이 아닌 같은 동(법정동) 단위 최근 계약을 참고로 보여드려요.</div>`
          : `<div class="text-gray-400 mt-1">국토교통부 실거래가 기준이며, 이 매물의 가격과는 다를 수 있어요.</div>`;
      body.innerHTML = list.length
        ? list.map(_referenceItemHtml).join("") + scopeNote
        : `<div class="text-gray-400">최근 2년간 실거래 내역을 찾지 못했어요.</div>`;
    } catch (e) {
      body.innerHTML = `<div class="text-gray-400">실거래 정보를 불러오지 못했어요. <button type="button" class="underline" data-ref-retry>다시 시도</button></div>`;
      body.querySelector("[data-ref-retry]")?.addEventListener("click", () => _loadReference(el), { once: true });
    }
  }

  /**
   * renderBody로 카드를 그린 뒤 호출한다(목록이 스크롤로 나눠 그려져도 매번 호출해도 안전 -
   * 이미 연결한 요소는 data-ref-wired로 건너뛴다). 펼치기를 기다리지 않고 카드가 그려지는 즉시
   * 그 매물의 GET /api/listings/{listingId}/reference를 호출해 채운다(2026-10-01, 이전엔 펼칠 때만
   * 조회했다 - 무한스크롤이 10건씩 끊어 그리는 덕에 한 번에 나가는 호출이 10개로 묶여 있고,
   * fetch_by_signature(단독다가구 경로)에도 캐시가 생겨 자치구+유형+계약월이 겹치면 호출을
   * 나눠 써서 감당할 만하다).
   * 단, 지도 핀 클릭(scrollToBuildingCard)이 목표 카드까지 loadAll()로 한 번에 다 렌더링하면
   * 이 배치 단위 가정이 깨져서 30위 밖은 애초에 자동조회 placeholder 자체를 안 만든다
   * (renderReferencePlaceholder의 autoLoad=false) - 그 카드들은 data-ref-manual-listing-id로만
   * 그려지고, 아래 위임 클릭 핸들러가 "실거래 조회하기" 버튼을 눌렀을 때만 조회한다.
   * @param rootEl 카드들이 들어있는 컨테이너 (예: 목록 <ul>)
   */
  function wireReferenceAutoLoad(rootEl) {
    rootEl.querySelectorAll("details[data-ref-listing-id]:not([data-ref-wired])").forEach((el) => {
      el.dataset.refWired = "1";
      _loadReference(el);
    });
    // 30위 밖(data-ref-manual-listing-id) "실거래 조회하기" 버튼 - 컨테이너당 한 번만 위임 연결한다
    // (매 배치마다 wireReferenceAutoLoad가 다시 불려도 rootEl.dataset로 중복 연결을 막는다).
    if (rootEl.dataset.refManualWired) return;
    rootEl.dataset.refManualWired = "1";
    rootEl.addEventListener("click", (e) => {
      const btn = e.target.closest("[data-ref-manual-btn]");
      if (!btn) return;
      const el = btn.closest("details[data-ref-manual-listing-id]");
      if (!el || el.dataset.refWired) return;
      el.dataset.refWired = "1";
      el.dataset.refListingId = el.dataset.refManualListingId;
      el.querySelector("[data-ref-body]").innerHTML = `<div class="text-gray-400">불러오는 중…</div>`;
      _loadReference(el);
    });
  }

  // 카드에 보이는 "통근 약 ~분"은 추천 응답의 직선거리 추정치다(검색/매칭을 가볍게 하려고 2026-10-01부터
  // 전체 후보에 카카오 API를 안 부른다). 화면에 "보이는" 카드에 대해서만(실거래 참고와 같은 패턴 - 무한스크롤이
  // 10건씩 끊어 그릴 때마다 그 배치만) GET .../commute로 그 매물 하나의 정확한 값을 불러와 갱신한다.
  async function _loadCommute(el, workLat, workLon, transportType) {
    try {
      const result = await CustomHouseWatchlistApi.getListingCommute(el.dataset.commuteListingId, workLat, workLon, transportType);
      el.textContent = `통근 약 ${result.commute_minutes}분`;
      el.title = result.commute_source || "카카오 API";
    } catch (e) {
      // 실패하면 직선거리 추정치를 그대로 보여준다 (조용히 무시)
    }
  }

  /**
   * renderBody로 카드를 그린 뒤 호출한다(이미 연결한 요소는 data-commute-wired로 건너뛴다).
   * 직장 좌표가 없으면(마이페이지 관심 매물처럼 통근 기준이 따로 없는 화면) 아무 것도 하지 않는다.
   * 추천 30위 밖 카드는 renderBody가 애초에 data-commute-listing-id를 안 붙여서 여기 셀렉터에
   * 걸리지 않는다 - 그 카드들은 직선거리 추정치가 갱신 없이 그대로 보인다(AUTO_REF_RANK_LIMIT 참고).
   * @param rootEl 카드들이 들어있는 컨테이너 (예: 목록 <ul>)
   */
  function wireCommuteAutoLoad(rootEl, workLat, workLon, transportType) {
    if (workLat == null || workLon == null) return;
    rootEl.querySelectorAll("[data-commute-listing-id]:not([data-commute-wired])").forEach((el) => {
      el.dataset.commuteWired = "1";
      _loadCommute(el, workLat, workLon, transportType);
    });
  }

  function renderBroker(b) {
    if (!b || !b.name) return "";
    return `
      <details class="listing-details">
        <summary>🏢 공인중개사 · ${esc(b.name)}</summary>
        <div class="body">
          <div>대표 ${esc(b.representative)} · 등록번호 ${esc(b.reg_no)}</div>
          <div>📞 ${esc(b.phone)}</div>
          <div>${esc(b.address)}</div>
          ${b.comment ? `<div class="mt-1">“${esc(b.comment)}”</div>` : ""}
        </div>
      </details>`;
  }

  /**
   * 카드 본문(사진 ~ 실거래 참고). 가격 표시: 월세는 "월세/보증금", 전세는 "전세 보증금"을 가장 크게. 관리비는 이 매물의 실제 관리비다.
   * @param r 리포트의 추천 매물 1건 (ListingRecommendation)
   * @param opts.idx 리포트 목록에서의 순번(0부터). 있으면 "#순위"와 지도 이동 링크가 붙고, 없으면(마이페이지) 건물명만 표시한다.
   * @param opts.conversionRate 보증금전환 이율(연 %). 없으면 매물에 함께 저장된 값(_conversion_rate)을 쓴다.
   */
  function renderBody(r, opts = {}) {
    const idx = opts.idx;
    // 실거래가/통근 정확값 둘 다 같은 기준으로 자동조회를 제한한다(마이페이지는 idx가 없어 그대로 자동조회, 리포트는 30위까지만) -
    // loadAll()이 목표 카드까지 한 번에 렌더링할 때 두 API를 수백~천 번씩 동시에 부르던 과부하 원인(2026-10-01).
    const autoLoadRef = idx == null || idx < AUTO_REF_RANK_LIMIT;
    const rate = opts.conversionRate ?? r._conversion_rate;
    const isJeonse = r.lease_type === "전세";
    const hasPhoto = r.photo && r.photo !== PHOTO_PLACEHOLDER;

    const headerRight = isJeonse
      ? `<div class="text-lg font-extrabold whitespace-nowrap" style="color:var(--brand-600)">전세 ${fmtMoney(r.listing_deposit)}</div>`
      : `
          <div class="text-lg font-extrabold whitespace-nowrap" style="color:var(--brand-600)">월세 ${r.listing_monthly_rent}만원</div>
          <div class="text-sm font-bold text-gray-700 mt-0.5 whitespace-nowrap">보증금 ${fmtMoney(r.listing_deposit)}</div>
        `;

    // 실질 주거비 = 월세 + 관리비 (교통비는 뺐다). 전세는 월세가 없어서, 매물 보증금 중 지금 가진 보증금으로
    // 못 채우는 부족분에 이자를 적용한 대출이자를 관리비에 더해 실제 부담을 보여준다(2026-09-29). 금리는 아래
    // 총보증금 전환 이자기회비용과 같은 값(전환율=한국부동산원 수도권 전월세 전환율 API) - 원금만 다르다(부족분 vs 보증금 전체).
    const rentTxt = `월세 ${fmtNum(r.listing_monthly_rent)}만`;
    const maintTxt = `관리비 ${fmtNum(r.maintenance_fee)}만`;
    // 보증금 예금전환 이자기회비용에 실제로 쓴 이율 - 월세는 정기예금(1년) 금리, 전세는 전월세 전환율(2026-10-03). 옛 저장본엔 없어서 전환율로 대체한다.
    const oppRate = r.deposit_opportunity_rate_percent ?? rate;
    const realCostSub = r.loan_interest > 0
      ? _subLines([maintTxt, `부족분(대출금액) ${fmtNum(r.deposit_shortfall)}만 대출이자 ${fmtNum(r.loan_interest)}만 (연 ${fmtRate(rate)}%)`])
      : isJeonse ? _subLines([maintTxt], ["(대출 없이 충분)"]) : _subLines([rentTxt, maintTxt]);
    const realCostTitle = r.loan_interest > 0
      ? `관리비 + 부족분(매물 보증금 - 현재 보증금) ${fmtNum(r.deposit_shortfall)}만 x 연 ${fmtRate(rate)}% / 12`
      : isJeonse ? "관리비 (보유 보증금으로 충분해 대출이자 없음)" : "월세 + 관리비";
    const costBoxes = `
      <div class="cost-boxes">
        <div class="cost-box" title="${realCostTitle}">
          <div class="lbl">실질 주거비</div>
          <div class="val">${fmtNum(r.real_housing_cost)}만원/월</div>
          <div class="sub">${realCostSub}</div>
        </div>
        <div class="cost-box conv" title="${isJeonse
          ? `월세 + 관리비 + 보증금 기회비용(보증금 x 연 ${fmtRate(oppRate)}% / 12, 수도권 전월세 전환율)`
          : `월세 + 관리비 + 보증금 기회비용(보증금 x 연 ${fmtRate(oppRate)}% / 12, 보증금을 예금에 넣었을 때의 이자 - 예금은행 정기예금 1년 금리)`}">
          <div class="lbl">${isJeonse ? "총보증금 전환 이자기회비용" : "보증금 예금전환 이자기회비용"}</div>
          <div class="val">${fmtNum(r.deposit_converted_cost)}만원/월</div>
          <div class="sub">${_subLines([rentTxt, maintTxt, `보증금 이자 ${fmtNum(r.deposit_opportunity_cost)}만 (연 ${fmtRate(oppRate)}%)`])}</div>
        </div>
      </div>`;

    // 관리자 화면에서 저장한 대출 조건을 통과한 대출 (AI 엔진이 매물마다 판별) - "[대출이름] 실질주거비"를 다른 비용 박스와 같은 모양으로 보여준다.
    // 2026-09-30: 대출별 실제 금리표(관리자 입력)가 있으면 그 금리를, 없으면 기준금리 API(총보증금 전환 이자기회비용과 같은 값)를
    // 대신 쓰고 우대금리만 반영한다 - is_temporary_rate가 어느 쪽인지 나타낸다.
    // 2026-10-02: 청년전용 보증부월세대출은 보증금이 충분해 대출이 필요 없어도(loan_principal=0) 월세대출은
    // 별개로 받을 수 있어서, 같은 대출을 "(월세 미대출 시)"/"(월세 대출 시)" 두 박스로 나눠서 보여준다
    // (rent_loan_total_cap_manwon이 있으면 = 이 대출에 월세대출 구조가 설정돼 있다는 뜻). 월세 대출 시는
    // 월세 중 대출로 충당되는 금액만큼 매달 현금으로 안 내지만 대출 잔액이 쌓여 만기에 갚아야 한다 - 빨간
    // 경고를 박스 라벨 옆에 같이 보여준다.
    const loanBoxes = (r.eligible_loans || [])
      .map((l) => {
        const hasRentLoan = l.rent_loan_total_cap_manwon != null;
        const depositBox = `
          <div class="cost-box loan" title="${
            l.is_temporary_rate
              ? "입력하신 조건이 이 대출의 자격 조건을 충족해요. 이 대출은 아직 실제 금리표가 없어 기준금리(보증금 전환율 API)에서 우대금리만 반영한 참고용이며, 실제 금리·한도·신청 가능 여부는 금융기관 심사로 확정돼요."
              : "입력하신 조건이 이 대출의 자격 조건을 충족해요. 관리자가 등록한 이 대출의 실제 금리표에서 우대금리를 반영한 값이며, 실제 한도·신청 가능 여부는 금융기관 심사로 확정돼요."
          }">
            <div class="lbl">${esc(l.name)} 실질주거비${hasRentLoan ? "<br />(월세 미대출 시)" : ""}</div>
            <div class="val">${fmtNum(l.effective_cost)}만원/월</div>
            <div class="sub">${
              l.loan_principal > 0
                ? _subLines([rentTxt, maintTxt, `부족분 ${fmtNum(l.loan_principal)}만 대출이자 ${fmtNum(l.monthly_interest)}만 (연 ${fmtRate(l.rate_percent)}%${l.is_temporary_rate ? ", 기준금리 적용" : ""})`])
                : _subLines([rentTxt, maintTxt], ["(보유 보증금으로 충분해 대출이 필요 없어요, 이자 0원)"])
            }</div>
            ${_loanWhyHtml(l)}
          </div>`;
        const rentLoanBox = !hasRentLoan || !(l.rent_loan_amount_manwon > 0) ? "" : `
          <div class="cost-box loan" title="월세 중 대출로 충당되는 금액(${fmtNum(l.rent_loan_amount_manwon)}만원)은 매달 현금으로 내지 않지만, 대출 잔액으로 쌓여서 만기(2년 가정)에 갚아야 해요.">
            <div class="lbl">${esc(l.name)} 실질주거비<br />(월세 대출 시)</div>
            <div class="val">${fmtNum(l.rent_loan_effective_cost)}만원/월</div>
            <div class="warn-badge">월세에 대한 대출금은 누적되어 만기시 상환해야함</div>
            <div class="cap-line">대출 기간 중 최대 월세대출액 ${fmtNum(l.rent_loan_total_cap_manwon)}만원 제한</div>
            <div class="sub">${_subLines([
              `월세 ${fmtNum(Math.max(0, (r.listing_monthly_rent || 0) - (l.rent_loan_amount_manwon || 0)))}만 (${fmtNum(r.listing_monthly_rent)}만 중 ${fmtNum(l.rent_loan_amount_manwon)}만 대출로 충당)`,
              maintTxt,
              l.loan_principal > 0 ? `보증금대출이자 ${fmtNum(l.monthly_interest)}만` : "",
              `월세대출이자 ${Number(l.rent_loan_monthly_interest || 0).toLocaleString()}원 (24개월 환산)`,
            ], [
              `2년간 총 이자 ${Number(l.rent_loan_total_interest || 0).toLocaleString()}원 예상`,
            ])}</div>
            ${_rentLoanWhyHtml(l, r)}
          </div>`;
        return depositBox + rentLoanBox;
      })
      .join("");

    const facts = [
      r.exclusive_area ? `${r.property_type === "단독다가구" ? "연면적" : "전용"} ${r.exclusive_area}㎡` : "",
      r.unit_label ? esc(r.unit_label) : "",
      r.rooms ? `방 ${r.rooms}·욕실 ${r.bathrooms ?? 1}` : "",
      r.elevator === true ? "엘리베이터" : r.elevator === false ? "엘리베이터 없음" : "",
      r.parking ? `주차 ${esc(r.parking)}` : "",
    ].filter(Boolean).join(" · ");

    const costBreakdown = `
      <div title="${esc(r.maintenance_fee_items || "")}">관리비 ${r.maintenance_fee}만${r.maintenance_fee_items && r.maintenance_fee_items !== "없음" ? " <span class=\"text-gray-400\">(" + esc(r.maintenance_fee_items) + ")</span>" : ""}</div>`;

    const nameHtml =
      idx == null
        ? `<span class="font-bold">${esc(r.building_name)}</span>`
        : `<button type="button" class="font-bold building-name-link" data-idx="${idx}" title="지도에서 이 매물 위치 보기">${esc(r.building_name)}</button>`;

    // 전세형/월세형은 항상 보여주고(반전세형은 그 중 월세형의 특수한 경우라 따로 추가로 붙는다),
    // 줄바꿈된 태그 줄(badge-row)에 모아서 매물명·통근시간과 겹치지 않게 분리한다.
    const leaseTypeBadge = isJeonse
      ? `<span class="text-xs font-semibold px-2 py-0.5 rounded-full lease-badge lease-jeonse">전세형</span>`
      : `<span class="text-xs font-semibold px-2 py-0.5 rounded-full lease-badge lease-wolse">월세형</span>`;

    const transportLabel = opts.transportLabel ?? TRANSPORT_TYPE_LABELS[r._transport_type] ?? null;

    return `
      <div class="listing-photo${hasPhoto ? "" : " empty"}">${hasPhoto ? `<img src="${esc(r.photo)}" alt="${esc(r.building_name)} 내부 사진" loading="lazy" />` : ""}</div>
      <div class="flex items-start justify-between mb-2">
        <div class="min-w-0">
          <div class="flex items-center flex-wrap gap-1">
            ${idx == null ? "" : `<span class="text-xs font-semibold px-2 py-0.5 rounded-full" style="background:var(--brand-100);color:var(--brand-700)">#${idx + 1}</span>`}
            ${nameHtml}
          </div>
          <div class="badge-row">
            ${r.property_type ? `<span class="text-xs px-2 py-0.5 rounded-full bg-gray-100 text-gray-600 whitespace-nowrap inline-block">${esc(r.property_type)}</span>` : ""}
            ${leaseTypeBadge}
            ${r.is_semi_jeonse ? `<span class="text-xs font-semibold px-2 py-0.5 rounded-full" style="background:#fef3c7;color:#92400e" title="보증금 ÷ 월세가 100 이상인 반전세형 매물이에요. 보증금이 부담되면 희망 보증금 조건으로 제외할 수 있어요.">반전세형</span>` : ""}
            ${r.jeonse_loan_available === false ? `<span class="text-xs font-semibold px-2 py-0.5 rounded-full" style="background:#fee2e2;color:#991b1b" title="등록자가 이 매물은 전세자금대출이 불가능하다고 표시했어요.">전세대출 불가</span>` : ""}
          </div>
          <div class="commute-time">
            <span aria-hidden="true">🚇</span>
            <span${autoLoadRef ? ` data-commute-listing-id="${esc(r.listing_id)}"` : ""} title="${esc(r.commute_source || "직선거리 추정")}">통근 약 ${r.commute_minutes}분</span>
            ${transportLabel ? `<span class="transport-tag">${esc(transportLabel)}</span>` : ""}
          </div>
        </div>
        <div class="text-right">${headerRight}</div>
      </div>
      <div class="text-sm text-gray-700 font-semibold mb-1 flex items-center gap-1">
        <span aria-hidden="true">📍</span>
        <span>${esc(r.address || `서울특별시 ${r.region}${r.dong ? ` ${r.dong}` : ""}`)}</span>
      </div>
      ${facts ? `<div class="text-xs text-gray-500 mb-1">${facts}</div>` : ""}
      ${r.move_in_date ? `<div class="text-xs text-gray-400 mb-1">이사가능일 ${esc(r.move_in_date)}</div>` : ""}
      <div class="text-xs text-gray-500 mt-2">${costBreakdown}</div>
      ${costBoxes}
      ${loanBoxes}
      ${r.description ? `<details class="listing-details"><summary>📝 상세 설명</summary><div class="body">${esc(r.description)}</div></details>` : ""}
      ${renderBroker(r.broker)}
      ${renderReferencePlaceholder(r.listing_id, autoLoadRef)}`;
  }

  window.CustomHouseListingCard = { PHOTO_PLACEHOLDER, esc, fmtMoney, fmtNum, fmtRate, renderBody, wireReferenceAutoLoad, wireCommuteAutoLoad };
})();
