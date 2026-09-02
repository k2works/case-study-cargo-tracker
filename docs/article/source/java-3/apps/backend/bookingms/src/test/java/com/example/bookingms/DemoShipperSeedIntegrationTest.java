package com.example.bookingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookingms.domain.model.aggregates.Shipper;
import com.example.bookingms.domain.repository.ShipperRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 動作確認用の荷主（{@code 9001} 番）が、**実業務の荷主として読める**ことを見る。
 *
 * <p>authms はこの番号へ {@code shipper01} を紐付けている。番号が違う相手を指すと、
 * 荷主の画面に<strong>他人の貨物が並ぶ</strong>——実際、種が無かったころは
 * 1 番がシミュレーションの作った荷主になっていた。
 */
@DisplayName("動作確認用の荷主の種")
class DemoShipperSeedIntegrationTest extends CargoPersistenceTestBase {

    /** authms の {@code V11__relink_demo_shipper.sql} が指す番号。**両方を同時に動かす**。 */
    private static final long DEMO_SHIPPER_ID = 9001L;

    @Autowired
    private ShipperRepository shippers;

    @Test
    @DisplayName("9001 番の荷主が読める（集約として復元できる）")
    void demoShipperIsRestorable() {
        Optional<Shipper> shipper = shippers.findById(DEMO_SHIPPER_ID);

        assertThat(shipper)
                .as("authms が紐付けている荷主が存在しない。荷主の画面が空になる")
                .isPresent();
    }

    /**
     * <strong>シミュレーションの帯にしない。</strong>{@code SIM-} で始めると
     * [ADR-030] 決定 3 によって一覧から外れ、動作確認の役に立たなくなる。
     */
    @Test
    @DisplayName("動作確認用の荷主は、シミュレーション由来ではない")
    void demoShipperIsNotSimulated() {
        Shipper shipper = shippers.findById(DEMO_SHIPPER_ID).orElseThrow();

        assertThat(shipper.shipperCode())
                .as("動作確認用の荷主がシミュレーションの帯にある")
                .doesNotStartWith("SIM-");
        // **同じ判定を書き直さない。**集約が持つ述語をそのまま呼ぶ
        assertThat(shipper.simulated())
                .as("動作確認用の荷主がシミュレーション由来として扱われる")
                .isFalse();
    }

    /**
     * <strong>採番とぶつからない位置に置く。</strong>採番は 1 から進むため、
     * 予約した番号に届く前に気づけるようにしておく。
     */
    @Test
    @DisplayName("採番はまだ予約した番号に届いていない")
    void sequenceHasNotReachedTheReservedId() {
        Long maxAutoAssigned = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM shipper WHERE id < ?", Long.class,
                DEMO_SHIPPER_ID);

        assertThat(maxAutoAssigned)
                .as("採番が予約した番号に近づいている。予約の位置を見直すこと")
                .isLessThan(DEMO_SHIPPER_ID);
    }
}
