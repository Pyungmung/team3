package com.customhouse.domain.user.dto;

/**
 * [담당: 허겸] 회원 도메인 - 광고·마케팅 목적 개인정보 수집·이용 동의 변경 요청.
 * nickname/phone과 별도 엔드포인트로 분리한 이유: UpdateProfileRequest에 같이 넣으면
 * "내 정보" 탭에서 이름만 바꿔 저장할 때도 이 값을 매번 같이 보내야 해서, 실수로 누락하면
 * 의도치 않게 동의가 false로 초기화되는 사고가 나기 쉽다.
 */
public record MarketingConsentRequest(boolean marketingConsent) {
}
