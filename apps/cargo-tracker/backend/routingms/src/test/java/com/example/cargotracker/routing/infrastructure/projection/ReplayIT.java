package com.example.cargotracker.routing.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.events.VoyageRegisteredEvent;
import com.example.cargotracker.routing.infrastructure.persistence.AttentionItemMapper;
import com.example.cargotracker.routing.infrastructure.persistence.VoyageMapper;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 投影のリプレイ（bookingms の {@code ReplayIT} と同じ形）。
 *
 * <p>ArchUnit の「投影がコマンドを送らない」はコンパイル時の依存しか見ておらず、
 * <b>実行時に呼ばれないことの保証ではない</b>。ここでは投影のハンドラをもう一度流し、
 * 副作用が積み上がらないことを確かめる。</p>
 *
 * <p><b>行が増えないだけでは足りない。</b> 投影は {@code attention_item}（追記専用の
 * 受け皿。リプレイで TRUNCATE しない）にも書く。ここが増えると、要確認一覧が同じ内容で
 * 膨らみ、経路設計者が毎朝見る一覧が信用されなくなる。</p>
 *
 * <p><b>IT3 時点で routingms に Reaction Handler は無い。</b> 入れたら、この IT に
 * 「読み直してもコマンドが再送されない」検査を足す（ADR-0001 決定 4。対応は
 * {@code ReplayCheckAccompaniesReactionTest} が機械的に見ている）。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReplayIT extends AbstractAxonIntegrationTest {

    private static final Instant DEPART = Instant.parse("2026-09-10T09:00:00Z");
    private static final Instant ARRIVE = Instant.parse("2026-09-24T18:00:00Z");

    @Autowired
    private VoyageProjection projection;

    @Autowired
    private VoyageMapper voyages;

    @Autowired
    private AttentionItemMapper attentionItems;

    private static VoyageRegisteredEvent voyage(String number, String vesselName) {
        return new VoyageRegisteredEvent(number, "MOL", "商船三井", vesselName,
                List.of(new VoyageRegisteredEvent.Movement("JPTYO", "USNYC", DEPART, ARRIVE)),
                List.of("GENERAL"), "routing01");
    }

    /** 航海番号は 20 文字まで。ナノ秒をそのまま繋ぐと桁あふれする。 */
    private static String uniqueNumber(String prefix) {
        return prefix + "-" + Long.toString(System.nanoTime(), 36);
    }

    private int openAttentionCount() {
        return attentionItems.findOpenByRole("ROLE_ROUTING").size();
    }

    @Test
    @DisplayName("同じイベントを読み直しても航海の行は増えない")
    void replayingVoyageIsIdempotent() {
        String number = uniqueNumber("VR");
        VoyageRegisteredEvent event = voyage(number, "MOL EXPRESS");

        projection.on(event);
        int before = voyages.countAll(true, null, Instant.now());
        projection.on(event);

        assertThat(voyages.countAll(true, null, Instant.now())).isEqualTo(before);
        assertThat(voyages.findMovements(number)).hasSize(1);
        assertThat(voyages.findAcceptedCargoTypes(number)).containsExactly("GENERAL");
    }

    @Test
    @DisplayName("弾かれた登録を読み直しても要確認一覧は増えない")
    void replayingRejectedRegistrationIsIdempotent() {
        String number = uniqueNumber("VJ");
        projection.on(voyage(number, "MOL EXPRESS"));
        // 同じ番号で中身が違う登録。1 度目で要確認に載る。
        VoyageRegisteredEvent duplicate = voyage(number, "ONE HARMONY");
        projection.on(duplicate);

        int before = openAttentionCount();
        projection.on(duplicate);

        assertThat(openAttentionCount()).isEqualTo(before);
    }
}
