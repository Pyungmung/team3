/**
 * [담당: 양혜승] 프론트엔드 클라이언트 키 설정 (견본)
 *
 * 이 프로젝트는 빌드 단계가 없는 순수 HTML/JS라 backend/customhouse-ai처럼 .env를 쓸 수 없다.
 * 그래서 브라우저에 그대로 노출되는(=원래 공개 전제인) 클라이언트 키만 여기 둔다.
 *
 * 사용법: 이 파일을 복사해서 config.js로 저장한 뒤 실제 값을 채워주세요.
 *   cp config.example.js config.js
 * (config.js는 .gitignore에 등록되어 있어 커밋되지 않습니다.)
 *
 * 카카오맵 JS 키 발급: https://developers.kakao.com → 내 애플리케이션 → 앱 키의 "JavaScript 키"
 *   (Kakao JS 키는 원래 클라이언트에 노출하는 용도이며, 대신 "플랫폼 > Web" 에 도메인을 등록해서
 *    다른 사이트에서의 무단 사용을 막는 구조다. 그래서 .env가 아니라 이렇게 JS 파일에 둬도 된다.)
 */
window.CUSTOMHOUSE_CONFIG = {
  KAKAO_MAP_APP_KEY: "",
};
