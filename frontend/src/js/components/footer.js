/**
 * [담당: 양혜승] 공통 UI 컴포넌트 - 푸터 (약관 링크 + 사업자 정보)
 * 사용법: 페이지의 <div id="app-footer"></div>에 renderFooter()를 호출한다.
 *   - 어두운 배경 페이지(회원가입 등)는 renderFooter({ dark: true })
 *
 * 2026-09-28: 부동산 서비스 푸터(직방 등)를 참고해 "약관 링크 한 줄 + 사업자 정보 4줄 + 저작권 + 데이터 안내"로 구성했다.
 * 약관 링크는 회원가입 화면의 "보기"와 같은 약관 화면(mypage/edit-condition.html?term=...#terms)으로 연결한다.
 * 아래 SITE_INFO의 사업자 정보는 아직 실제 값이 아닌 자리표시자(000-00-00000 등)이니, 사업자 등록이 끝나면 여기만 고친다.
 * Tailwind가 없는 페이지(회원가입 등)에서도 똑같이 보이도록 스타일은 이 파일이 스스로 주입한다(.ch-footer*).
 * 지도 화면(report-listings.html)은 화면을 꽉 채우는 레이아웃이라 푸터를 두지 않는다.
 */
const SITE_INFO = {
  companyName: "맞집",
  representative: "커스텀하우스",
  businessNumber: "000-00-00000",
  address: "서울 서초구 동작대로 132 안석빌딩 9층",
  mailOrderNumber: "제2026-서울서초-00000호",
  email: "rnltjd747@gmail.com",
  partnershipEmail: "rnltjd747@gmail.com", // 서비스제휴문의
  adEmail: "rnltjd747@gmail.com", // 분양광고 문의
  fax: "02-000-0000",
};

function renderFooter(options = {}) {
  const el = document.getElementById("app-footer");
  if (!el) return;

  if (!document.getElementById("ch-footer-style")) {
    const style = document.createElement("style");
    style.id = "ch-footer-style";
    style.textContent = `
      .ch-footer { width: 100%; margin-top: 48px; background: #f8fafc; border-top: 1px solid #e2e8f0;
        font-family: Arial, "Apple SD Gothic Neo", "Malgun Gothic", sans-serif; }
      .ch-footer-inner { max-width: 1440px; margin: 0 auto; padding: 26px 24px 30px; }
      .ch-footer-terms { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 0; margin-bottom: 18px; }
      .ch-footer-terms a { font-size: 14px; font-weight: 500; color: #1e293b; text-decoration: none; }
      .ch-footer-terms a.strong { font-weight: 800; }
      .ch-footer-terms a:hover { text-decoration: underline; text-underline-offset: 3px; }
      .ch-footer .bar { display: inline-block; width: 1px; height: 11px; margin: 0 14px; background: #cbd5e1; vertical-align: middle; }
      .ch-footer-info { font-size: 13px; line-height: 2; color: #64748b; }
      .ch-footer-info p { margin: 0; }
      .ch-footer-info a { color: inherit; text-decoration: none; }
      .ch-footer-info a:hover { text-decoration: underline; text-underline-offset: 3px; }
      .ch-footer-info .bar { margin: 0 10px; }
      .ch-footer-info .f { white-space: nowrap; }
      /* 좁은 화면: 구분선을 숨기고 항목마다 한 줄씩 (구분선이 줄 끝에 남거나 라벨과 값이 갈라지지 않게) */
      @media (max-width: 639px) {
        .ch-footer .bar { display: none; }
        .ch-footer-terms a { margin-right: 16px; }
        .ch-footer-info .f { display: block; white-space: normal; }
      }
      .ch-footer-copy { margin: 16px 0 0; font-size: 13px; font-weight: 600; line-height: 1.7; color: #64748b; }
      .ch-footer-note { margin: 2px 0 0; font-size: 12px; line-height: 1.7; color: #94a3b8; }
      @media (min-width: 640px) { .ch-footer-inner { padding-left: 48px; padding-right: 48px; } }

      /* 어두운 배경 페이지용 (회원가입 등) */
      .ch-footer.dark { margin-top: 0; background: transparent; border-top: 1px solid rgba(255, 255, 255, 0.18); }
      .ch-footer.dark .ch-footer-inner { max-width: 980px; padding: 18px 4px 6px; }
      .ch-footer.dark .ch-footer-terms a { color: #fff; }
      .ch-footer.dark .bar { background: rgba(255, 255, 255, 0.35); }
      .ch-footer.dark .ch-footer-info { color: rgba(255, 255, 255, 0.72); }
      .ch-footer.dark .ch-footer-copy { color: rgba(255, 255, 255, 0.8); }
      .ch-footer.dark .ch-footer-note { color: rgba(255, 255, 255, 0.55); }
    `;
    document.head.appendChild(style);
  }

  // header.js의 computeHref와 같은 규칙: /pages/ 아래 몇 단계 깊이인지에 따라 "../"를 붙인다 (header.js 없이도 동작하게 따로 둔다)
  const depth = window.location.pathname.split("/pages/")[1]?.split("/").length - 1 || 0;
  const href = (target) => "../".repeat(Math.max(depth, 0)) + target;
  const termHref = (key) => href(`mypage/edit-condition.html?term=${key}#terms`);

  const bar = '<span class="bar" aria-hidden="true"></span>';
  const f = (text) => `<span class="f">${text}</span>`;
  const mail = (address) => `<a href="mailto:${address}">${address}</a>`;
  const s = SITE_INFO;

  el.innerHTML = `
    <footer class="ch-footer${options.dark ? " dark" : ""}">
      <div class="ch-footer-inner">
        <nav class="ch-footer-terms" aria-label="약관">
          <a href="${termHref("service")}">이용약관</a>${bar}<a class="strong" href="${termHref("privacy")}">개인정보 처리방침</a>${bar}<a href="${termHref("location")}">위치기반 서비스 이용약관</a>
        </nav>
        <div class="ch-footer-info">
          <p>${[`상호 : ${s.companyName}`, `대표 : ${s.representative}`, `사업자등록번호 : ${s.businessNumber}`].map(f).join(bar)}</p>
          <p>${f(`주소 : ${s.address}`)}</p>
          <p>${f(`통신판매업 신고번호 : ${s.mailOrderNumber}`)}</p>
          <p>${[`이메일 : ${mail(s.email)}`, `서비스제휴문의 : ${mail(s.partnershipEmail)}`, `분양광고 문의 : ${mail(s.adEmail)}`, `팩스 : ${s.fax}`].map(f).join(bar)}</p>
        </div>
        <p class="ch-footer-copy">Copyright © CUSTOMHOUSE. All Rights Reserved.</p>
        <p class="ch-footer-note">SMU 11기 Team3 · 본 서비스의 매물·시세·정책 데이터는 예시(샘플)이며 실제와 다를 수 있습니다.</p>
      </div>
    </footer>
  `;
}
