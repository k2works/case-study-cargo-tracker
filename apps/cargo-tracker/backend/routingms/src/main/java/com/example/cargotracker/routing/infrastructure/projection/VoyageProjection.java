package com.example.cargotracker.routing.infrastructure.projection;

import com.example.cargotracker.routing.domain.model.events.VoyageRegisteredEvent;
import com.example.cargotracker.routing.infrastructure.persistence.VoyageMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 航海の投影（Processing Group: routing-voyage-projection）。
 *
 * <p>Processing Group はこのクラスのパッケージ名で {@code application.yml} に書く。
 * {@code @ProcessingGroup} は Axon 5 に無い（ADR-0001 決定 3）。</p>
 *
 * <p><b>投影はコマンドを送らない。</b> 送るとリプレイのたびに副作用が再実行される。
 * 一意制約で弾いた行は {@code attention_item} に残し、経路設計者の一覧（S70）に出す。</p>
 *
 * <p>航海番号の一意は三段の 2 段目と 3 段目をここで守る。1 段目（集約の存在確認）は
 * 同時登録のレースで素通りするので、ここが最後の砦になる。</p>
 */
@Component
public class VoyageProjection {

    private static final Logger log = LoggerFactory.getLogger(VoyageProjection.class);

    private final VoyageMapper voyages;
    private final AttentionItemRecorder attentionItems;
    private final Clock clock;

    public VoyageProjection(VoyageMapper voyages, AttentionItemRecorder attentionItems,
            Clock clock) {
        this.voyages = voyages;
        this.attentionItems = attentionItems;
        this.clock = clock;
    }

    @EventHandler
    public void on(VoyageRegisteredEvent event) {
        Instant now = clock.instant();

        List<VoyageRegisteredEvent.Movement> movements = event.movements();
        VoyageRegisteredEvent.Movement first = movements.get(0);
        VoyageRegisteredEvent.Movement last = movements.get(movements.size() - 1);

        VoyageMapper.VoyageRow row = new VoyageMapper.VoyageRow(
                event.voyageNumber(),
                event.carrierCode(),
                event.carrierName(),
                event.vesselName(),
                first.departureUnLocode(),
                last.arrivalUnLocode(),
                first.departureAt(),
                last.arrivalAt(),
                false,
                now,
                now,
                null);

        // 一意制約は例外ではなく戻り値で見る。例外にすると PostgreSQL が
        // トランザクションを中断し、捕まえても外側（投影とトークンの書き込み）が
        // 巻き添えになる。トークンが進まないので、その 1 件で投影全体が止まる。
        int inserted = voyages.insert(row);

        if (inserted == 0 && !sameAsStored(row, movements, event.acceptedCargoTypes())) {
            // 弾かれた。集約は受け付けているので、ここで黙ると
            // 「登録したのに一覧に出ない」が誰にも見えないまま残る。
            log.warn("航海の投影を一意制約で弾いた: voyageNumber={}", event.voyageNumber());
            attentionItems.add("PROJECTION_REJECTED", "VOYAGE", event.voyageNumber(),
                    "ROLE_ROUTING", "航海番号の重複", "{}", now);
            return;
        }

        // 入らなかったが中身が同じなら、リプレイで同じイベントを読み直しただけ。
        // 寄港地と貨物種別は書き続ける。1 度目が途中で落ちていたら、ここで揃う。
        for (int i = 0; i < movements.size(); i++) {
            VoyageRegisteredEvent.Movement movement = movements.get(i);
            voyages.insertMovement(new VoyageMapper.MovementRow(
                    event.voyageNumber(),
                    i + 1,
                    movement.departureUnLocode(),
                    movement.arrivalUnLocode(),
                    movement.departureAt(),
                    movement.arrivalAt()));
        }
        for (String cargoType : event.acceptedCargoTypes()) {
            voyages.insertAcceptedCargoType(event.voyageNumber(), cargoType);
        }
    }

    /**
     * 既にある行と丸ごと比べる。
     *
     * <p>航海番号は主キーであると同時に業務の識別子なので、投影からは
     * 「同じイベントの読み直し」と「別の内容で同じ番号を登録された」が区別できない。
     * <b>項目ごとに比べない。</b> 積み上げると、航海に属性が増えるたびに比べ忘れが
     * 生まれ、別物が読み直し扱いで黙って捨てられる。</p>
     *
     * <p>投影の時刻（{@code registeredAt} / {@code projectedAt} / {@code lastEventId}）は
     * 業務の内容ではないので、比べる前に既存の行の値へ揃える。</p>
     *
     * <p><b>ヘッダの行だけでは足りない。</b> {@code voyage} が持つのは最初の出発と
     * 最後の到着だけで、途中の寄港地と受入貨物種別は別の表にある。ヘッダだけを比べると、
     * 「同じ区間だが途中の寄港地が違う」「同じ区間だが危険物も受けると言っている」2 件目が
     * 読み直し扱いになり、そのまま {@code insertAcceptedCargoType} まで進んで
     * <b>既存の航海の受入種別が黙って広がる</b>。要確認一覧にも載らない。
     * 比べるのは投影が保持する内容の全部である。</p>
     */
    private boolean sameAsStored(VoyageMapper.VoyageRow candidate,
            List<VoyageRegisteredEvent.Movement> movements, List<String> acceptedCargoTypes) {
        VoyageMapper.VoyageRow stored = voyages.findByNumber(candidate.voyageNumber());
        if (stored == null) {
            return false;
        }
        VoyageMapper.VoyageRow normalized = new VoyageMapper.VoyageRow(
                candidate.voyageNumber(), candidate.carrierCode(), candidate.carrierName(),
                candidate.vesselName(), candidate.departureUnlocode(),
                candidate.arrivalUnlocode(), candidate.departureAt(), candidate.arrivalAt(),
                candidate.cancelled(),
                stored.registeredAt(), stored.projectedAt(), stored.lastEventId());
        return normalized.equals(stored)
                && sameMovements(candidate.voyageNumber(), movements)
                && sameAcceptedCargoTypes(candidate.voyageNumber(), acceptedCargoTypes);
    }

    /** 寄港地は順序も内容のうち。並びが違えば別の航海である。 */
    private boolean sameMovements(String voyageNumber,
            List<VoyageRegisteredEvent.Movement> movements) {
        List<VoyageMapper.MovementRow> stored = voyages.findMovements(voyageNumber);
        if (stored.size() != movements.size()) {
            return false;
        }
        for (int i = 0; i < movements.size(); i++) {
            VoyageRegisteredEvent.Movement movement = movements.get(i);
            VoyageMapper.MovementRow expected = new VoyageMapper.MovementRow(
                    voyageNumber, i + 1,
                    movement.departureUnLocode(), movement.arrivalUnLocode(),
                    movement.departureAt(), movement.arrivalAt());
            if (!expected.equals(stored.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 受入貨物種別は集合として比べる。並びは意味を持たないが、
     * <b>数が違えば別物</b>である（追記しかしないので、広がったことに気づけない）。
     */
    private boolean sameAcceptedCargoTypes(String voyageNumber, List<String> acceptedCargoTypes) {
        return new java.util.TreeSet<>(voyages.findAcceptedCargoTypes(voyageNumber))
                .equals(new java.util.TreeSet<>(acceptedCargoTypes));
    }
}
