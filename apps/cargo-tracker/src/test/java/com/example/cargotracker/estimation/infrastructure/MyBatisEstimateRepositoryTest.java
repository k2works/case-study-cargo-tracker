package com.example.cargotracker.estimation.infrastructure;

import com.example.cargotracker.estimation.domain.model.Estimate;
import com.example.cargotracker.estimation.domain.model.EstimateId;
import com.example.cargotracker.estimation.domain.model.RouteCandidate;
import com.example.cargotracker.estimation.infrastructure.repositories.MyBatisEstimateRepository;
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
@DisplayName("MyBatisEstimateRepository 統合テスト")
class MyBatisEstimateRepositoryTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private MyBatisEstimateRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE route_candidate, estimate RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("Estimate を保存して EstimateId で取得できる")
    void shouldSaveAndFindByEstimateId() {
        Estimate estimate = Estimate.create(
                "JPTYO", "USNYC",
                LocalDate.now().plusDays(30),
                "GENERAL",
                new BigDecimal("1000.000")
        );

        repository.save(estimate);

        Optional<Estimate> found = repository.findByEstimateId(estimate.getEstimateId());
        assertThat(found).isPresent();
        assertThat(found.get().getOriginUnlocode()).isEqualTo("JPTYO");
        assertThat(found.get().getDestinationUnlocode()).isEqualTo("USNYC");
        assertThat(found.get().getCargoType()).isEqualTo("GENERAL");
    }

    @Test
    @DisplayName("ルート候補を含む Estimate を保存して取得できる")
    void shouldSaveAndFindEstimateWithCandidates() {
        Estimate estimate = Estimate.create(
                "JPTYO", "USNYC",
                LocalDate.now().plusDays(30),
                "GENERAL",
                new BigDecimal("1000.000")
        );
        estimate.addCandidates(List.of(
                new RouteCandidate("V001", "SGSIN", 21, new BigDecimal("500000.00")),
                new RouteCandidate("V002", "HKHKG", 28, new BigDecimal("480000.00"))
        ));

        repository.save(estimate);

        Optional<Estimate> found = repository.findByEstimateId(estimate.getEstimateId());
        assertThat(found).isPresent();
        assertThat(found.get().getCandidates()).hasSize(2);
    }

    @Test
    @DisplayName("全件一覧を取得できる")
    void shouldFindAll() {
        Estimate est1 = Estimate.create("JPTYO", "USNYC", LocalDate.now().plusDays(30), "GENERAL", new BigDecimal("1000.000"));
        Estimate est2 = Estimate.create("JPTYO", "SGSIN", LocalDate.now().plusDays(45), "REFRIGERATED", new BigDecimal("500.000"));
        repository.save(est1);
        repository.save(est2);

        List<Estimate> all = repository.findAll();
        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("存在しない EstimateId で取得すると empty が返る")
    void shouldReturnEmptyWhenNotFound() {
        Optional<Estimate> found = repository.findByEstimateId(EstimateId.generate());
        assertThat(found).isEmpty();
    }
}
