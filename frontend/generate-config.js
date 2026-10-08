/**
 * [담당: Claude] Vercel 빌드 시 config.js를 생성한다.
 *
 * frontend/src/js/config.js는 카카오맵 JS 키가 든 파일인데 .gitignore에 등록되어 있어
 * 로컬에서만 각자 만들어 쓰고 Git엔 올라가지 않는다 - 그래서 Vercel이 저장소를 그대로
 * 빌드하면 이 파일 자체가 없어 카카오맵이 항상 "키 없음" 폴백으로 빠졌다.
 * Vercel Build Command(`node generate-config.js`)가 이 스크립트를 실행해, Vercel
 * 프로젝트 환경변수 KAKAO_MAP_APP_KEY 값으로 config.js를 빌드 시점에 만들어 넣는다.
 * (카카오 JS 키는 원래 브라우저 노출이 정상이라 - 카카오 개발자센터의 도메인 등록으로
 * 막는 구조 - Vercel 환경변수도 "Sensitive" 아닌 일반 값으로 넣어도 된다.)
 *
 * 로컬 개발(python serve.py)은 이 스크립트를 실행하지 않으므로, 로컬 config.js는
 * 지금처럼 직접 만든 파일 그대로 영향받지 않는다.
 */
const fs = require("fs");
const path = require("path");

const kakaoMapAppKey = process.env.KAKAO_MAP_APP_KEY || "";
// 구글 AdSense (2026-10-08): 게시자 ID(ca-pub-숫자)와 기본 광고 단위 ID(숫자). 둘 다 브라우저에 노출되는 공개 값이라 Vercel 일반 환경변수로 둔다.
// 형식이 맞지 않으면(잘못 붙여넣은 값) 빈 값으로 두어 광고 자리가 AdSense를 불러오지 않게 한다.
const adsenseClient = /^ca-pub-\d+$/.test(process.env.ADSENSE_CLIENT || "") ? process.env.ADSENSE_CLIENT : "";
const adsenseSlot = /^\d+$/.test(process.env.ADSENSE_SLOT || "") ? process.env.ADSENSE_SLOT : "";

const content = `window.CUSTOMHOUSE_CONFIG = {
  KAKAO_MAP_APP_KEY: "${kakaoMapAppKey}",
  ADSENSE_CLIENT: "${adsenseClient}",
  ADSENSE_SLOT: "${adsenseSlot}",
};
`;

const outPath = path.join(__dirname, "src", "js", "config.js");
fs.writeFileSync(outPath, content);
console.log(`generate-config.js: wrote ${outPath} (KAKAO_MAP_APP_KEY ${kakaoMapAppKey ? "set" : "empty"}, ADSENSE_CLIENT ${adsenseClient ? "set" : "empty"}, ADSENSE_SLOT ${adsenseSlot ? "set" : "empty"})`);
