package com.customhouse.domain.listing.dto;

// [담당: 송귀성] 매물 등록 요청의 입력 범위 검증 - 상식 밖 값(음수 관리비, 100억 초과 보증금, 이상한 날짜/사진 주소 등)은 AI 엔진까지 가지 않고 400으로 거절한다 (2026-10-07).

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class ListingRegistrationRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** 모든 값이 정상인 기본 요청 - 필드 하나만 바꿔 본다 */
    private record Builder(int deposit, int monthlyRent, double area, Integer rooms, Integer bathrooms, Integer builtYear, Integer maintenanceFee,
                           String moveInDate, String photoUrl) {
        static Builder ok() {
            return new Builder(1000, 50, 23.5, 2, 1, 2015, 5, "2026-11-01", "/uploads/listings/a1b2.jpg");
        }

        ListingRegistrationRequest build() {
            return new ListingRegistrationRequest("서울 서초구 동작대로 132", "오피스텔", "월세", deposit, monthlyRent, area, "건물", "101호", "3", rooms, bathrooms,
                    builtYear, maintenanceFee, "수도", "가능", true, moveInDate, "설명", true, photoUrl, null, null, null, null, null, null);
        }
    }

    private Set<String> invalidFields(UnaryOperator<Builder> change) {
        Set<ConstraintViolation<ListingRegistrationRequest>> v = validator.validate(change.apply(Builder.ok()).build());
        return v.stream().map(x -> x.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void 정상_요청과_비워둔_선택_값은_통과한다() {
        assertThat(invalidFields(b -> b)).isEmpty();
        assertThat(invalidFields(b -> new Builder(b.deposit, b.monthlyRent, b.area, null, null, null, null, "", ""))).isEmpty();
        // 수정 폼은 예전에 저장된 절대 주소 사진(로컬 업로드)도 그대로 다시 보낸다
        assertThat(invalidFields(b -> new Builder(b.deposit, b.monthlyRent, b.area, b.rooms, b.bathrooms, b.builtYear, b.maintenanceFee, b.moveInDate,
                "http://localhost:8080/uploads/listings/a1b2.jpg"))).isEmpty();
    }

    @Test
    void 상식_밖_숫자는_거절한다() {
        assertThat(invalidFields(b -> new Builder(b.deposit, b.monthlyRent, b.area, b.rooms, b.bathrooms, b.builtYear, -3, b.moveInDate, b.photoUrl))).containsExactly("maintenanceFee");
        assertThat(invalidFields(b -> new Builder(b.deposit, b.monthlyRent, b.area, b.rooms, b.bathrooms, b.builtYear, 1001, b.moveInDate, b.photoUrl))).containsExactly("maintenanceFee");
        assertThat(invalidFields(b -> new Builder(1_000_001, b.monthlyRent, b.area, b.rooms, b.bathrooms, b.builtYear, b.maintenanceFee, b.moveInDate, b.photoUrl))).containsExactly("deposit");
        assertThat(invalidFields(b -> new Builder(b.deposit, 5001, b.area, b.rooms, b.bathrooms, b.builtYear, b.maintenanceFee, b.moveInDate, b.photoUrl))).containsExactly("monthlyRent");
        assertThat(invalidFields(b -> new Builder(b.deposit, b.monthlyRent, 1000.5, b.rooms, b.bathrooms, b.builtYear, b.maintenanceFee, b.moveInDate, b.photoUrl))).containsExactly("exclusiveArea");
        assertThat(invalidFields(b -> new Builder(b.deposit, b.monthlyRent, b.area, -1, 21, b.builtYear, b.maintenanceFee, b.moveInDate, b.photoUrl))).containsExactlyInAnyOrder("rooms", "bathrooms");
        assertThat(invalidFields(b -> new Builder(b.deposit, b.monthlyRent, b.area, b.rooms, b.bathrooms, 1800, b.maintenanceFee, b.moveInDate, b.photoUrl))).containsExactly("builtYear");
        assertThat(invalidFields(b -> new Builder(b.deposit, b.monthlyRent, b.area, b.rooms, b.bathrooms, 2100, b.maintenanceFee, b.moveInDate, b.photoUrl))).containsExactly("builtYear");
    }

    @Test
    void 이상한_날짜와_사진_주소는_거절한다() {
        assertThat(invalidFields(b -> new Builder(b.deposit, b.monthlyRent, b.area, b.rooms, b.bathrooms, b.builtYear, b.maintenanceFee, "내일", b.photoUrl))).containsExactly("moveInDate");
        assertThat(invalidFields(b -> new Builder(b.deposit, b.monthlyRent, b.area, b.rooms, b.bathrooms, b.builtYear, b.maintenanceFee, "2026/11/01", b.photoUrl))).containsExactly("moveInDate");
        for (String bad : new String[]{"javascript:alert(1)", "http://evil.example/x.png", "/other/a.jpg", "/uploads/listings/../../etc/passwd", "/uploads/listings/"}) {
            assertThat(invalidFields(b -> new Builder(b.deposit, b.monthlyRent, b.area, b.rooms, b.bathrooms, b.builtYear, b.maintenanceFee, b.moveInDate, bad)))
                    .as(bad).containsExactly("photoUrl");
        }
    }
}
