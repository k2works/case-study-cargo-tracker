package com.example.cargotracker.billing.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.UnLocode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 輸送実績（US21 §受入基準 2）。
 *
 * <p><b>書いた守りは対で赤にする。</b> 「重量が無ければ算出できない」は本 IT の
 * 中心にある判断（注 N1）なので、落としても赤にならない状態にしない。</p>
 */
class TransportRecordTest {

    private static final UnLocode TOKYO = new UnLocode("JPTYO");
    private static final UnLocode OSAKA = new UnLocode("JPOSA");
    private static final UnLocode NEW_YORK = new UnLocode("USNYC");
    private static final List<TransportRecord.BilledLeg> LEGS =
            List.of(new TransportRecord.BilledLeg(TOKYO, OSAKA));

    private static TransportRecord record(List<TransportRecord.BilledLeg> legs, String weightKg,
            String cargoType, UnLocode origin, UnLocode destination) {
        return new TransportRecord(legs, weightKg == null ? null : new BigDecimal(weightKg),
                cargoType, origin, destination);
    }

    @Test
    @DisplayName("注 N1: 重量が分からなければ作れない（安い請求を黙って出さない）")
    void refusesMissingWeight() {
        assertThatThrownBy(() -> record(LEGS, null, "GENERAL", TOKYO, OSAKA))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("重量");
        assertThatThrownBy(() -> record(LEGS, "0", "GENERAL", TOKYO, OSAKA))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> record(LEGS, "-1", "GENERAL", TOKYO, OSAKA))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("区間・貨物種別・両端が無ければ作れない")
    void refusesMissingInputs() {
        assertThatThrownBy(() -> record(List.of(), "1000", "GENERAL", TOKYO, OSAKA))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> record(null, "1000", "GENERAL", TOKYO, OSAKA))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> record(LEGS, "1000", " ", TOKYO, OSAKA))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> record(LEGS, "1000", null, TOKYO, OSAKA))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> record(LEGS, "1000", "GENERAL", null, OSAKA))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> record(LEGS, "1000", "GENERAL", TOKYO, null))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new TransportRecord.BilledLeg(TOKYO, null))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new TransportRecord.BilledLeg(null, OSAKA))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("輸出は出発地と目的地の国で決まる（区間の遠さではない）")
    void decidesExportByCountries() {
        assertThat(record(LEGS, "1000", "GENERAL", TOKYO, NEW_YORK).isExport()).isTrue();
        assertThat(record(LEGS, "1000", "GENERAL", TOKYO, OSAKA).isExport()).isFalse();
    }
}
