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
    }

    @Test
    @DisplayName("見積を作成するとルート候補が生成され保存される")
    void shouldCreateEstimateWithRouteCandidates() {
        CreateEstimateCommand command = new CreateEstimateCommand(
                "JPTYO", "USNYC",
                LocalDate.now().plusDays(30),
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
