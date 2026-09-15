/**
 * [담당: 양혜승] 공통 UI 컴포넌트 - 푸터
 * 사용법: 페이지의 <div id="app-footer"></div>에 renderFooter()를 호출한다.
 */
function renderFooter() {
  const el = document.getElementById("app-footer");
  if (!el) return;

  el.innerHTML = `
    <footer class="w-full border-t border-gray-100 mt-16">
      <div class="max-w-5xl mx-auto px-4 py-8 text-xs text-gray-400 flex flex-col sm:flex-row gap-2 sm:justify-between">
        <span>맞집(CustomHouse) · SMU 11기 · Team3</span>
        <span>본 서비스의 시세/정책 데이터는 예시(샘플)이며 실제와 다를 수 있습니다.</span>
      </div>
    </footer>
  `;
}
