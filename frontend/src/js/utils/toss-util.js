/**
 * [담당: 양혜승] 토스페이먼츠 결제창 SDK 공통 유틸
 * 각 결제 페이지(checkout.html 등)에서 <script src="https://js.tosspayments.com/v2/standard">를
 * 먼저 로드한 뒤 이 파일을 사용한다.
 *
 * clientKey는 토스페이먼츠 공식 샘플(tosspayments/tosspayments-sample)에 공개된 테스트 키입니다.
 * 실제 결제가 발생하지 않는 샌드박스 키라 별도 가입 없이 바로 테스트할 수 있습니다.
 * (백엔드 secret-key와 쌍을 이루는 키이며, backend/src/main/resources/application.yml 참고)
 */
const TOSS_CLIENT_KEY = "test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq";
const TOSS_CUSTOMER_KEY_STORAGE = "customhouse:tossCustomerKey";

function getTossPayments() {
  if (typeof TossPayments === "undefined") {
    throw new Error("토스페이먼츠 SDK가 로드되지 않았습니다.");
  }
  return TossPayments(TOSS_CLIENT_KEY);
}

/** 브라우저(사용자)당 하나의 customerKey를 유지한다 (빌링키 발급/청구 시 동일 값이 필요). */
function getOrCreateCustomerKey() {
  let key = localStorage.getItem(TOSS_CUSTOMER_KEY_STORAGE);
  if (!key) {
    key = "customer_" + window.btoa(String(Math.random())).replace(/[^a-zA-Z0-9]/g, "").slice(0, 20);
    localStorage.setItem(TOSS_CUSTOMER_KEY_STORAGE, key);
  }
  return key;
}

function generateOrderId(prefix) {
  return `${prefix}_${window.btoa(String(Math.random())).replace(/[^a-zA-Z0-9]/g, "").slice(0, 16)}`;
}

window.CustomHouseTossUtil = { getTossPayments, getOrCreateCustomerKey, generateOrderId };
