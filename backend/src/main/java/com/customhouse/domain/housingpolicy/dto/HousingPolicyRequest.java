package com.customhouse.domain.housingpolicy.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * [담당: 송귀성] 주거지원정책 저장 요청 (관리자 수정 > 주거지원정책). 한 줄(정책 1건)의 모든 칸이다.
 * 금액은 만원, 나이는 만 나이. 숫자 칸을 비우면(null) "제한 없음", 체크 칸은 체크하면 그 조건이 필요한 정책이다.
 */
public record HousingPolicyRequest(
        @NotBlank(message = "지역이 필요합니다.")
        @Pattern(regexp = "서울|강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구",
                message = "지역은 서울(공통) 또는 서울시 25개 자치구 중 하나여야 합니다.") String region,
        @NotBlank(message = "기관명을 입력해주세요.")
        @Size(max = 100, message = "기관명은 100자 이하로 입력해주세요.") String agency,
        @NotBlank(message = "지원정책명을 입력해주세요.")
        @Size(max = 150, message = "지원정책명은 150자 이하로 입력해주세요.") String name,
        @NotBlank(message = "지원혜택을 입력해주세요.")
        @Size(max = 5000, message = "지원혜택은 5000자 이하로 입력해주세요.") String description,
        @Min(value = 0, message = "최소 나이는 0 이상이어야 합니다.")
        @Max(value = 120, message = "최소 나이는 120 이하로 입력해주세요.") Integer minAge,
        @Min(value = 0, message = "최대 나이는 0 이상이어야 합니다.")
        @Max(value = 120, message = "최대 나이는 120 이하로 입력해주세요.") Integer maxAge,
        @Min(value = 0, message = "연소득 상한은 0 이상이어야 합니다.")
        @Max(value = 1_000_000, message = "연소득 상한이 너무 큽니다.") Integer maxAnnualIncome,
        @Min(value = 0, message = "총자산 상한은 0 이상이어야 합니다.")
        @Max(value = 1_000_000, message = "총자산 상한이 너무 큽니다.") Integer maxAsset,
        @Min(value = 0, message = "기준중위소득 비율은 0 이상이어야 합니다.")
        @Max(value = 1000, message = "기준중위소득 비율은 1000% 이하로 입력해주세요.") Integer medianIncomePercent,
        boolean requireBasicLivelihood,
        boolean requireSme,
        boolean requireNewlywed,
        boolean requireNoHousehold,
        boolean loan,
        @Size(max = 300, message = "특이사항은 300자 이하로 입력해주세요.") String note,
        // 정책 안내 페이지 주소. 화면에서 그대로 링크로 쓰므로 http(s)만 허용한다 (javascript: 같은 주소 차단). 없어도 된다.
        @Size(max = 500, message = "링크는 500자 이하로 입력해주세요.")
        @Pattern(regexp = "^$|^[Hh][Tt][Tt][Pp][Ss]?://\\S+$", message = "링크는 http:// 또는 https://로 시작해야 합니다.") String link
) {

    @AssertTrue(message = "최소 나이가 최대 나이보다 클 수 없습니다.")
    public boolean isAgeRangeValid() {
        return minAge == null || maxAge == null || minAge <= maxAge;
    }
}
