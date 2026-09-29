/**
 * [담당: 송귀성] 추천 매물 카드 - 리포트(report-listings.html)와 마이페이지 관심 매물(watchlist/list.html)이 같이 쓰는 카드 본문.
 * 관심 매물로 담을 때 리포트 카드의 매물 정보를 통째로 저장해 두고, 마이페이지에서 이 함수로 리포트와 같은 카드를 다시 그린다.
 * 스타일은 css/listing-card.css, 버튼/신고 표시 같은 페이지별 부분은 각 페이지가 카드 본문 앞뒤에 붙인다.
 * 매물 설명/중개사 문구는 사용자가 직접 입력할 수 있으니 화면에 넣기 전에 항상 esc()로 이스케이프한다.
 */
(function () {
  const PHOTO_PLACEHOLDER = "PHOTO_PLACEHOLDER";

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

  // 실거래가는 카드 맨 아래 "실거래 참고"에 이전 실거래 내역처럼 보조로만 보여준다.
  function renderReference(ref) {
    if (!ref) return "";
    const isJeonse = (ref.monthly_rent || 0) === 0;
    const price = isJeonse
      ? `전세 ${fmtMoney(ref.deposit)}`
      : `보증금 ${fmtMoney(ref.deposit)} / 월세 ${ref.monthly_rent}만원`;
    const renewal =
      ref.contract_type === "갱신" && (ref.pre_deposit || ref.pre_monthly_rent)
        ? `<div>종전 계약: 보증금 ${fmtMoney(ref.pre_deposit)} / 월세 ${ref.pre_monthly_rent ?? 0}만원 · 갱신요구권 ${ref.use_rr_right === "Y" ? "사용" : "미사용"}</div>`
        : "";
    return `
      <details class="listing-details">
        <summary>📊 실거래 참고 (국토부 · 이 건물의 최근 계약)</summary>
        <div class="body">
          <div><b>${esc(ref.contract_date)}</b> ${esc(ref.contract_type)} · ${price}</div>
          <div>${ref.area ? `${ref.area}㎡` : ""}${ref.floor ? ` · ${esc(ref.floor)}층` : ""}${ref.contract_term ? ` · 계약기간 ${esc(ref.contract_term)}` : ""}</div>
          ${renewal}
          <div class="text-gray-400 mt-1">국토교통부 실거래가 기준이며, 이 매물의 가격과는 다를 수 있어요.</div>
        </div>
      </details>`;
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
    // 보증금액 전환 이자기회비용과 같은 값(전환율=한국부동산원 수도권 전월세 전환율 API) - 원금만 다르다(부족분 vs 보증금 전체).
    const realCostSub = r.loan_interest > 0
      ? `관리비 + 부족분(대출금액) ${fmtNum(r.deposit_shortfall)}만 대출이자 ${fmtNum(r.loan_interest)}만 (연 ${fmtRate(rate)}%)`
      : isJeonse ? "관리비 (대출 없이 충분)" : "월세 + 관리비";
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
        <div class="cost-box conv" title="월세 + 관리비 + 보증금 기회비용(보증금 x 연 ${fmtRate(rate)}% / 12)">
          <div class="lbl">보증금액 전환 이자기회비용</div>
          <div class="val">${fmtNum(r.deposit_converted_cost)}만원/월</div>
          <div class="sub">월세 + 관리비 + 보증금 이자 ${fmtNum(r.deposit_opportunity_cost)}만 (연 ${fmtRate(rate)}%)</div>
        </div>
      </div>`;

    // 관리자 화면에서 저장한 대출 조건을 통과한 대출 (AI 엔진이 매물마다 판별) - "[대출이름] 실질주거비"를 다른 비용 박스와 같은 모양으로 보여준다.
    // 2026-09-30: 대출별 실제 금리표(관리자 입력)가 있으면 그 금리를, 없으면 기준금리 API(보증금액 전환 이자기회비용과 같은 값)를
    // 대신 쓰고 우대금리만 반영한다 - is_temporary_rate가 어느 쪽인지 나타낸다.
    const loanBoxes = (r.eligible_loans || [])
      .map(
        (l) => `
          <div class="cost-box loan" title="${
            l.is_temporary_rate
              ? "입력하신 조건이 이 대출의 자격 조건을 충족해요. 이 대출은 아직 실제 금리표가 없어 기준금리(보증금 전환율 API)에서 우대금리만 반영한 참고용이며, 실제 금리·한도·신청 가능 여부는 금융기관 심사로 확정돼요."
              : "입력하신 조건이 이 대출의 자격 조건을 충족해요. 관리자가 등록한 이 대출의 실제 금리표에서 우대금리를 반영한 값이며, 실제 한도·신청 가능 여부는 금융기관 심사로 확정돼요."
          }">
            <div class="lbl">${esc(l.name)} 실질주거비</div>
            <div class="val">${fmtNum(l.effective_cost)}만원/월</div>
            <div class="sub">${
              l.loan_principal > 0
                ? `월세 + 관리비 + 부족분 ${fmtNum(l.loan_principal)}만 대출이자 ${fmtNum(l.monthly_interest)}만 (연 ${fmtRate(l.rate_percent)}%${l.is_temporary_rate ? ", 기준금리 적용" : ""})`
                : "보유 보증금으로 충분해 대출이 필요 없어요 (이자 0원)"
            }</div>
          </div>`
      )
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
        ? `<span class="font-bold ml-2">${esc(r.building_name)}</span>`
        : `<button type="button" class="font-bold ml-2 building-name-link" data-idx="${idx}" title="지도에서 이 매물 위치 보기">${esc(r.building_name)}</button>`;

    return `
      <div class="listing-photo">${hasPhoto ? `<img src="${esc(r.photo)}" alt="${esc(r.building_name)} 내부 사진" loading="lazy" />` : "📷 내부 사진 준비 중"}</div>
      <div class="flex items-start justify-between mb-2">
        <div>
          ${idx == null ? "" : `<span class="text-xs font-semibold px-2 py-0.5 rounded-full" style="background:var(--brand-100);color:var(--brand-700)">#${idx + 1}</span>`}
          ${nameHtml}
          ${r.property_type ? `<span class="text-xs px-2 py-0.5 rounded-full ml-1 bg-gray-100 text-gray-600 whitespace-nowrap inline-block">${esc(r.property_type)}</span>` : ""}
          ${r.is_semi_jeonse ? `<span class="text-xs font-semibold px-2 py-0.5 rounded-full ml-1" style="background:#fef3c7;color:#92400e" title="보증금 ÷ 월세가 100 이상인 반전세형 매물이에요. 보증금이 부담되면 희망 보증금 조건으로 제외할 수 있어요.">반전세형</span>` : ""}
          <span class="text-xs text-gray-400 ml-1" title="${esc(r.commute_source || "카카오 API")}">통근 약 ${r.commute_minutes}분</span>
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
      ${renderReference(r.reference_transaction)}`;
  }

  window.CustomHouseListingCard = { PHOTO_PLACEHOLDER, esc, fmtMoney, fmtNum, fmtRate, renderBody };
})();
