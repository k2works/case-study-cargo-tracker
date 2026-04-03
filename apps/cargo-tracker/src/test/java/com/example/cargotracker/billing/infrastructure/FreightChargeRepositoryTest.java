package com.example.cargotracker.billing.infrastructure;

import com.example.cargotracker.billing.domain.model.aggregates.FreightCharge;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.domain.model.repository.FreightChargeRepository;
import com.example.cargotracker.billing.domain.model.valueobjects.ChargeStatus;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("FreightChargeRepository 統合テスト")
class FreightChargeRepositoryTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private FreightChargeRepository freightChargeRepository;

    @Test
    @DisplayName("輸送料金を保存して ID で取得できる")
    void saveAndFindById() {
        FreightId id = FreightId.generate();
        FreightCharge charge = FreightCharge.calculate(id, "BK-001", new BigDecimal("1000"));

        freightChargeRepository.save(charge);

        Optional<FreightCharge> found = freightChargeRepository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(id);
        assertThat(found.get().getBookingId()).isEqualTo("BK-001");
        assertThat(found.get().getStatus()).isEqualTo(ChargeStatus.DRAFT);
        assertThat(found.get().getBaseAmount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(found.get().getAdjustmentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(found.get().getTotalAmount()).isEqualByComparingTo(new BigDecimal("1000"));
    }

    @Test
    @DisplayName("輸送料金を保存して予約 ID で取得できる")
    void saveAndFindByBookingId() {
        FreightCharge charge1 = FreightCharge.calculate(FreightId.generate(), "BK-002", new BigDecimal("1500"));
        FreightCharge charge2 = FreightCharge.calculate(FreightId.generate(), "BK-002", new BigDecimal("2000"));

        freightChargeRepository.save(charge1);
        freightChargeRepository.save(charge2);

        List<FreightCharge> found = freightChargeRepository.findByBookingId("BK-002");
        assertThat(found).hasSize(2);
    }

    @Test
    @DisplayName("調整額を適用した輸送料金が正しく保存・復元される")
    void saveAndFindById_withAdjustment() {
        FreightId id = FreightId.generate();
        FreightCharge charge = FreightCharge.calculate(id, "BK-003", new BigDecimal("1000"));
        charge.applyAdjustment(new BigDecimal("200"));

        freightChargeRepository.save(charge);

        Optional<FreightCharge> found = freightChargeRepository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getAdjustmentAmount()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(found.get().getTotalAmount()).isEqualByComparingTo(new BigDecimal("1200"));
    }

    @Test
    @DisplayName("確定済みの輸送料金が正しく保存・復元される")
    void saveAndFindById_confirmed() {
        FreightId id = FreightId.generate();
        FreightCharge charge = FreightCharge.calculate(id, "BK-004", new BigDecimal("1000"));
        charge.confirm();

        freightChargeRepository.save(charge);

        Optional<FreightCharge> found = freightChargeRepository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(ChargeStatus.CONFIRMED);
    }

    @Test
    @DisplayName("更新した輸送料金が正しく保存される（save で insert/update を自動判断）")
    void save_updateExisting() {
        FreightId id = FreightId.generate();
        FreightCharge charge = FreightCharge.calculate(id, "BK-005", new BigDecimal("1000"));
        freightChargeRepository.save(charge);

        // 調整額を適用して再保存
        charge.applyAdjustment(new BigDecimal("500"));
        freightChargeRepository.save(charge);

        Optional<FreightCharge> found = freightChargeRepository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getAdjustmentAmount()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(found.get().getTotalAmount()).isEqualByComparingTo(new BigDecimal("1500"));
    }

    @Test
    @DisplayName("存在しない ID では空の Optional を返す")
    void findById_notFound_returnsEmpty() {
        Optional<FreightCharge> found = freightChargeRepository.findById(FreightId.generate());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("存在しない予約 ID では空リストを返す")
    void findByBookingId_notFound_returnsEmpty() {
        List<FreightCharge> found = freightChargeRepository.findByBookingId("BK-NOT-FOUND");
        assertThat(found).isEmpty();
    }
}
