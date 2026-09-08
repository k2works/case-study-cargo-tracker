package com.example.cargotracker.handling.interfaces.rest;

import com.example.cargotracker.handling.domain.model.commands.RegisterHandlingActivityCommand;
import com.example.cargotracker.handling.domain.model.commands.VoidHandlingActivityCommand;
import com.example.cargotracker.handling.domain.model.valueobjects.CargoSnapshot;
import com.example.cargotracker.handling.infrastructure.persistence.CargoSnapshots;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.handling.infrastructure.persistence.CargoSnapshotMapper;
import com.example.cargotracker.handling.infrastructure.persistence.HandlingActivityMapper;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CargoOnVoyageListView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CargoSnapshotView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindAwaitingClaimQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCargoSnapshotQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCargosOnVoyageQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindHandlingHistoryQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindVoyagePortsQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.HandlingHistoryView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.VoyagePortListView;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 荷役の記録（S50・S51 / UC13・US15）。
 *
 * <p><b>予定ルート外の判定はここで解決する</b>（不変条件 2）。Axon のコマンド
 * ハンドラは読み取りモデルを引数に取れないので、{@code CargoSnapshot} を引ける
 * この層で判定してからコマンドに載せる。<b>判定そのものは
 * {@code CargoSnapshot#isOffRoute} の 1 か所</b>で、ここに書き直さない。</p>
 *
 * <p><b>反映を待たない</b>（ui_design.md S50）。荷役作業員は 1 隻から 20〜50 本を
 * 連続で記録するので、1 本ごとに投影を待つと現場が止まる。コマンドの応答で完了とする。</p>
 */
@RestController
@RequestMapping("/api/v1/handling")
public class HandlingController {

    private final CommandGateway commands;
    private final QueryDispatcher queries;
    private final CargoSnapshotMapper cargos;
    private final HandlingActivityMapper activities;
    private final Clock clock;

    /**
     * 同じ内容の記録を断る間隔（不変条件 5）。
     *
     * <p>読取機の二度打ちと、2 人が同じ貨物を記録したときを断る。長くすると
     * 「同じ港で降ろして積み直す」正当な作業まで断ってしまう。</p>
     */
    private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(5);

    public HandlingController(CommandGateway commands, QueryDispatcher queries,
            CargoSnapshotMapper cargos, HandlingActivityMapper activities, Clock clock) {
        this.commands = commands;
        this.queries = queries;
        this.cargos = cargos;
        this.activities = activities;
        this.clock = clock;
    }

    /**
     * 荷役の記録（S50）。
     *
     * @param activityId クライアントが作る冪等キー。<b>サーバは採らない</b>——
     *     通信断で再送したときに別の鍵になって二重に記録される
     */
    public record RegisterRequest(
            @NotBlank(message = "活動 ID は必須です") String activityId,
            @NotBlank(message = "追跡番号は必須です") String trackingNumber,
            @NotBlank(message = "作業種別は必須です") String handlingType,
            @NotBlank(message = "作業場所は必須です") String unLocode,
            String voyageNumber,
            String consigneeName,
            Instant completedAt) {
    }

    /** 取り消し（S50 の送信済みの行）。 */
    public record VoidRequest(@NotBlank(message = "理由は必須です") String reason) {
    }

    /**
     * これから作業する航海と港（S02 荷役のダッシュボード）。
     *
     * <p><b>件数だけでは仕事が進まない。</b> 現場は追跡番号を持っていないので、
     * 航海と港から入れないと S50 が始まらない（IT4 の「気づく手段は次の行動へ繋ぐ」）。</p>
     */
    @GetMapping("/voyages")
    public ResponseEntity<VoyagePortListView> voyagePorts() {
        return ResponseEntity.ok(queries.query(
                new FindVoyagePortsQuery(), VoyagePortListView.class));
    }

    /** この航海がこの港で降ろす貨物（S50 の起点）。 */
    @GetMapping("/voyages/{voyageNumber}/cargos")
    public ResponseEntity<CargoOnVoyageListView> cargosOnVoyage(
            @PathVariable String voyageNumber,
            @RequestParam String unLocode) {
        return ResponseEntity.ok(queries.query(
                new FindCargosOnVoyageQuery(voyageNumber, unLocode.toUpperCase(
                        java.util.Locale.ROOT)),
                CargoOnVoyageListView.class));
    }

    /**
     * その港で引取を待っている貨物（H.8 / US16）。
     *
     * <p><b>航海起点では辿り着けない。</b> 引取は船から降りたあとの作業で、
     * どの航海の仕事でもない（S02 荷役の下部タブ「引取待ち」）。</p>
     */
    @GetMapping("/awaiting-claim")
    public ResponseEntity<CargoOnVoyageListView> awaitingClaim(
            @RequestParam String unLocode) {
        return ResponseEntity.ok(queries.query(
                new FindAwaitingClaimQuery(unLocode.toUpperCase(java.util.Locale.ROOT)),
                CargoOnVoyageListView.class));
    }

    /**
     * 貨物 1 件の写し（S50 の「確認」欄）。
     *
     * <p><b>見つからないときは 404。</b> US15 §受入基準 6——存在しない追跡番号を
     * 打ったことが分からないと、作業員は記録できたつもりで次へ進む。</p>
     */
    @GetMapping("/cargos/{trackingNumber}")
    public ResponseEntity<CargoSnapshotView> cargo(@PathVariable String trackingNumber,
            @RequestParam(required = false) String unLocode) {
        // **港を渡すと、種別ごとに予定外かどうかも返る**（H.5）。判定を画面に
        // 書き直させない。S51（荷役履歴）は港を持たないので渡さない。
        CargoSnapshotView view = queries.query(
                new FindCargoSnapshotQuery(trackingNumber, unLocode), CargoSnapshotView.class);
        return view == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }

    /** 荷役履歴（S51）。 */
    @GetMapping("/{trackingNumber}/activities")
    public ResponseEntity<HandlingHistoryView> history(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(queries.query(
                new FindHandlingHistoryQuery(trackingNumber), HandlingHistoryView.class));
    }

    /** 荷役を記録する（US15 §受入基準 1〜4・7）。 */
    @PostMapping("/activities")
    public ResponseEntity<Void> register(
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody RegisterRequest request) {
        HandlingType type = typeOf(request.handlingType());
        String unLocode = request.unLocode().toUpperCase(java.util.Locale.ROOT);
        CargoSnapshot snapshot = snapshotOf(request.trackingNumber());
        Instant completedAt = request.completedAt() == null
                ? clock.instant() : request.completedAt();
        rejectRecentDuplicate(snapshot.trackingNumber(), type, unLocode, completedAt);

        commands.sendAndWait(new RegisterHandlingActivityCommand(request.activityId(),
                snapshot.trackingNumber(), snapshot.bookingId(), type, unLocode,
                request.voyageNumber(),
                // **判定は CargoSnapshot が答える。** ここに書き直さない。
                snapshot.isOffRoute(type, Location.of(unLocode)),
                Location.of(unLocode).equals(snapshot.destination()),
                request.consigneeName(),
                username, completedAt),
                String.class);

        return ResponseEntity.created(URI.create(
                "/api/v1/handling/activities/" + request.activityId())).build();
    }

    /**
     * 同じ内容の記録が直近にあれば断る（不変条件 5）。
     *
     * <p><b>集約では守れないのでここに置く。</b> 1 作業 1 集約で、集約は他の作業を
     * 知らない（`activityId` が違えば別の集約）。判定に他の作業が要る規則は、
     * 旅程を引くのと同じ層で解決する。<b>正典（data-model）は「集約が守る」と
     * 書いていたが実装できない</b>——IT9 のレビューで分かり、正典を直した。</p>
     *
     * <p>冪等キー（同一 `activityId` の再送）とは別の守り。あちらは<b>同じ送信</b>
     * の重複を、こちらは<b>別々の送信で同じ内容</b>の重複を断る。</p>
     */
    private void rejectRecentDuplicate(String trackingNumber, HandlingType type,
            String unLocode, Instant completedAt) {
        int recent = activities.countRecentDuplicates(trackingNumber, type.name(), unLocode,
                completedAt.minus(DUPLICATE_WINDOW));
        if (recent > 0) {
            throw new BusinessRuleViolation(
                    "同じ貨物の" + type.label() + "が " + DUPLICATE_WINDOW.toMinutes()
                            + " 分以内に記録されています。取り違えでなければ、"
                            + "前の記録を取り消してから記録し直してください");
        }
    }

    /** 記録を取り消す（S50 の送信済みの行 / 不変条件 7）。 */
    @PostMapping("/activities/{activityId}/void")
    public ResponseEntity<Void> voidActivity(@PathVariable String activityId,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody VoidRequest request) {
        commands.sendAndWait(new VoidHandlingActivityCommand(activityId, request.reason(),
                username), Void.class);
        return ResponseEntity.noContent().build();
    }

    /**
     * 追跡番号から貨物の写しを引く。
     *
     * <p><b>見つからなければ断る</b>（US15 §受入基準 6）。予定ルートの判定も
     * 予約 ID の解決もできないので、記録しても誰にも紐づかない。</p>
     */
    private CargoSnapshot snapshotOf(String trackingNumber) {
        var row = cargos.findByTrackingNumber(trackingNumber);
        if (row == null) {
            throw new BusinessRuleViolation(
                    "追跡番号 " + trackingNumber + " の貨物が見つかりません");
        }
        // **組み立ては 1 か所**（読みの経路と同じ形にする）。
        return CargoSnapshots.of(row, cargos.findLegs(trackingNumber));
    }

    /** 種別の名前を型に直す。<b>知らない名前を 500 にしない</b>（入力の誤り）。 */
    private static HandlingType typeOf(String name) {
        try {
            return HandlingType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolation("知らない作業種別です: " + name);
        }
    }
}
