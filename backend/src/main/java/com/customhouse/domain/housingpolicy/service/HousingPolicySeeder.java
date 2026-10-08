package com.customhouse.domain.housingpolicy.service;

import com.customhouse.domain.housingpolicy.repository.HousingPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * [담당: 송귀성] 주거지원정책 초기 시드 (2026-10-08). 예전에 docs/housing_policy_list.csv -> policies.json으로 쓰던 439건을
 * seed/housing_policies.json(CSV 순서 그대로)에서 읽어, housing_policies 테이블이 비어 있을 때 한 번만 넣는다.
 * 한 건이라도 있으면(관리자가 이미 수정했거나 이전에 시드했으면) 건드리지 않는다 - 이후 정책은 관리자 수정 > 주거지원정책에서만 고친다.
 * IncomeStandardSeeder처럼 공개 데이터라 운영(prod)에도 필요하므로 프로필 제한이 없다.
 *
 * 클라우드 DB는 쿼리 1번이 0.6초쯤 걸려서 JPA로 한 줄씩 넣으면 4~5분이 걸리고, 그 사이 서버가 꺼지면 일부만 들어간 채로 남아
 * 다시는 시드되지 않는다. 그래서 한 번의 다중 행 INSERT를 하나의 트랜잭션으로 넣는다(전부 들어가거나 전혀 안 들어간다).
 * 한 INSERT의 행들은 적은 순서대로 연속된 id를 받으므로 id 순서가 곧 리포트 표시 순서(= CSV 줄 순서)다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HousingPolicySeeder implements ApplicationRunner {

    private static final TypeReference<List<SeedRow>> ROWS_TYPE = new TypeReference<>() {
    };
    static final String SEED_RESOURCE = "seed/housing_policies.json";
    private static final int COLUMNS = 18;
    private static final String INSERT_PREFIX = "INSERT INTO housing_policies (region, agency, name, description, min_age, max_age, "
            + "max_annual_income, max_asset, median_income_percent, require_basic_livelihood, require_sme, require_newlywed, "
            + "require_no_household, is_loan, note, link, created_at, updated_at) VALUES ";
    private static final String ROW_PLACEHOLDERS = "(" + "?,".repeat(COLUMNS - 1) + "?)";

    private final HousingPolicyRepository policyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (policyRepository.count() > 0) {
            return;
        }
        List<SeedRow> rows = readSeedRows();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> insertAll(rows));
        log.info("주거지원정책 초기값 {}건을 시드했습니다.", rows.size());
    }

    List<SeedRow> readSeedRows() throws IOException {
        try (InputStream in = new ClassPathResource(SEED_RESOURCE).getInputStream()) {
            return json.readValue(in, ROWS_TYPE);
        }
    }

    private void insertAll(List<SeedRow> rows) {
        String sql = INSERT_PREFIX + String.join(",", java.util.Collections.nCopies(rows.size(), ROW_PLACEHOLDERS));
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<Object> args = new ArrayList<>(rows.size() * COLUMNS);
        for (SeedRow r : rows) {
            args.add(r.region());
            args.add(r.agency());
            args.add(r.name());
            args.add(r.description());
            args.add(r.minAge());
            args.add(r.maxAge());
            args.add(r.maxAnnualIncome());
            args.add(r.maxAsset());
            args.add(r.medianIncomePercent());
            args.add(r.requireBasicLivelihood());
            args.add(r.requireSme());
            args.add(r.requireNewlywed());
            args.add(r.requireNoHousehold());
            args.add(r.loan());
            args.add(r.note());
            args.add(r.link());
            args.add(now);
            args.add(now);
        }
        jdbcTemplate.update(sql, args.toArray());
    }

    /** seed/housing_policies.json 한 줄 */
    record SeedRow(String region, String agency, String name, String description, Integer minAge, Integer maxAge,
                   Integer maxAnnualIncome, Integer maxAsset, Integer medianIncomePercent, boolean requireBasicLivelihood,
                   boolean requireSme, boolean requireNewlywed, boolean requireNoHousehold, boolean loan, String note, String link) {
    }
}
