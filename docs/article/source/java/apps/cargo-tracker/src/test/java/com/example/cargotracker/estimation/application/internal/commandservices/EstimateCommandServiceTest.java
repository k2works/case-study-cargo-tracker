package com.example.cargotracker.estimation.application.internal.commandservices;

import com.example.cargotracker.estimation.domain.model.Estimate;
import com.example.cargotracker.estimation.domain.model.EstimateId;
import com.example.cargotracker.estimation.domain.model.EstimateStatus;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("EstimateCommandService 統合テスト")
class EstimateCommandServiceTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private EstimateCommandService estimateCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE route_candidate, estimate RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE carrier_movement, voyage RESTART IDENTITY CASCADE");
        // テスト用航海データを挿入（JPTYO → USNYC、90 日以内に到着）
        jdbcTemplate.execute("INSERT INTO voyage (voyage_number) VALUES ('TV001')");
        jdbcTemplate.update(
                "INSERT INTO carrier_movement (voyage_id, departure_location_unlocode, arrival_location_unlocode, departure_date, arrival_date) " +
                "VALUES ((SELECT id FROM voyage WHERE voyage_number = 'TV001'), 'JPTYO', 'USNYC', ?, ?)",
                java.sql.Timestamp.valueOf(LocalDate.now().plusDays(5).atStartOfDay()),
                java.sql.Timestamp.valueOf(LocalDate.now().plusDays(35).atStartOfDay()));
    }

    @Test
    @DisplayName("見積を作成するとルート候補が生成され保存される")
    void shouldCreateEstimateWithRouteCandidates() {
        CreateEstimateCommand command = new CreateEstimateCommand(
                "JPTYO", "USNYC",
                LocalDate.now().plusDays(90),
                "GENERAL",
                new BigDecimal("1000.000")
        );

        EstimateId estimateId = estimateCommandService.createEstimate(command);

        assertThat(estimateId).isNotNull();
        Optional<Estimate> found = estimateCommandService.findByEstimateId(estimateId);
        assertThat(found).isPresent();
        assertThat(found.get().getCandidates()).isNotEmpty();
        assertThat(found.get().getStatus()).isEqualTo(EstimateStatus.CREATED);
    }

    @Test
    @DisplayName("全件一覧を取得できる")
    void shouldFindAllEstimates() {
        estimateCommandService.createEstimate(new CreateEstimateCommand(
                "JPTYO", "USNYC", LocalDate.now().plusDays(30), "GENERAL", new BigDecimal("1000.000")));
        estimateCommandService.createEstimate(new CreateEstimateCommand(
                "JPTYO", "SGSIN", LocalDate.now().plusDays(45), "REFRIGERATED", new BigDecimal("500.000")));

        List<Estimate> all = estimateCommandService.findAll();
        assertThat(all).hasSize(2);
    }
}
