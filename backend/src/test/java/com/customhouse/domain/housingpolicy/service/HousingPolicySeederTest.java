package com.customhouse.domain.housingpolicy.service;

// [담당: 송귀성] 주거지원정책 시더 - 비어 있을 때만 seed/housing_policies.json(CSV 순서 그대로)을 한 번에 넣고, 이미 있으면 건드리지 않는다 (2026-10-08).
// 단위 테스트는 시드 파일 내용/건너뛰기를, 통합 테스트(SpringBootTest, H2)는 실제로 DB에 순서대로 들어가는지를 확인한다.

import com.customhouse.domain.housingpolicy.entity.HousingPolicy;
import com.customhouse.domain.housingpolicy.repository.HousingPolicyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class HousingPolicySeederTest {

    private static final Set<String> REGIONS = Set.of("서울", "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구", "노원구", "도봉구",
            "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구", "성북구", "송파구", "양천구", "영등포구", "용산구", "은평구", "종로구", "중구", "중랑구");

    @Autowired
    private HousingPolicyRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 시드_파일은_439건이고_모든_행이_필수_값과_올바른_지역을_가진다() throws Exception {
        List<HousingPolicySeeder.SeedRow> rows = new HousingPolicySeeder(repository, jdbcTemplate, transactionManager).readSeedRows();

        assertThat(rows).hasSize(439);
        assertThat(rows).allSatisfy(r -> {
            assertThat(REGIONS).contains(r.region());
            assertThat(r.agency()).isNotBlank();
            assertThat(r.name()).isNotBlank();
            assertThat(r.description()).isNotBlank();
            assertThat(r.link()).startsWith("http");
        });
        assertThat(rows.stream().filter(HousingPolicySeeder.SeedRow::loan).count()).isEqualTo(12);
    }

    @Test
    void 서버가_켜질_때_빈_테이블에_시드_순서대로_들어간다() {
        // 컨텍스트가 뜨면서 시더가 이미 실행됐다 (H2는 테스트마다 새로 만들어져 처음엔 비어 있다)
        List<HousingPolicy> all = repository.findAllByOrderByIdAsc();

        assertThat(all).hasSize(439);
        HousingPolicy first = all.get(0);
        assertThat(first.getName()).isEqualTo("버팀목 전세 대출");   // CSV 첫 줄
        assertThat(first.getRegion()).isEqualTo("서울");
        assertThat(first.isLoan()).isTrue();
        assertThat(first.isRequireNoHousehold()).isTrue();
        assertThat(first.getMinAge()).isEqualTo(19);
        assertThat(first.getMaxAge()).isEqualTo(34);
        assertThat(first.getMaxAnnualIncome()).isEqualTo(5000);
        assertThat(first.getMaxAsset()).isEqualTo(34500);
        assertThat(first.getMedianIncomePercent()).isNull();
        assertThat(first.getLink()).startsWith("https://");
        assertThat(first.getCreatedAt()).isNotNull();
        // id가 연속이라 id 순서가 곧 시드 파일(CSV) 순서다
        for (int i = 1; i < all.size(); i++) {
            assertThat(all.get(i).getId()).isEqualTo(all.get(i - 1).getId() + 1);
        }
    }

    @Test
    void 이미_정책이_있으면_다시_넣지_않는다() throws Exception {
        HousingPolicyRepository mocked = mock(HousingPolicyRepository.class);
        when(mocked.count()).thenReturn(3L);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        new HousingPolicySeeder(mocked, jdbc, transactionManager).run(null);

        verify(jdbc, never()).update(any(String.class), any(Object[].class));
        assertThat(repository.count()).isEqualTo(439);   // 실제 테이블은 그대로
    }
}
