package com.example.cargotracker.tracking.interfaces.rest;

import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import com.example.cargotracker.tracking.domain.model.commands.NotifyShipperOfExceptionCommand;
import com.example.cargotracker.tracking.domain.model.commands.RegisterTrackingExceptionCommand;
import com.example.cargotracker.tracking.domain.model.commands.ResolveTrackingExceptionCommand;
import com.example.cargotracker.tracking.domain.model.commands.StartExceptionResponseCommand;
import com.example.cargotracker.tracking.domain.model.commands.UpdateTransportStatusCommand;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionType;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.CountRecentlyChangedQuery;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.FindTrackingQuery;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.ExceptionListView;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.FindOpenExceptionsQuery;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.RecentlyChangedView;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.FindTrackingsQuery;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.TrackingListView;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.TrackingView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.time.Clock;
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
 * 追跡一覧・詳細と状態の手動更新（S40・S41 / US17・US18）。
 *
 * <p><b>追跡管理者と荷主の両方が使う。</b> 荷主には自社のぶんだけを出す
 * （{@code ui_design.md:144-145} は S40・S41 のロールを「追跡、荷主（自社のみ）」と定める）。</p>
 *
 * <p><b>絞り込みはヘッダの荷主 ID で行う。</b> Gateway が JWT から取り出して
 * {@code X-Auth-Shipper-Id} で伝える。クライアントの指定を信じると、他社の追跡まで
 * 見えてしまう。</p>
 *
 * <p><b>「荷主 ID が無ければ全件」にしない。</b> 荷主 ID の紐付いていない
 * {@code ROLE_SHIPPER} の利用者が 1 人でも作られると、その人に全社の追跡が見える
 * （{@code user_shipper_link} は NULL を許し、JWT も claim ごと落とす）。
 * <b>判断の材料はロール</b>にする——{@code ROLE_SHIPPER} を名乗るなら荷主 ID は必須で、
 * 無ければ断る。フェイルオープンにしない。</p>
 */
@RestController
@RequestMapping("/api/v1/tracking/trackings")
public class TrackingController {

    private final QueryDispatcher queries;
    private final CommandGateway commands;
    private final Clock clock;

    public TrackingController(QueryDispatcher queries, CommandGateway commands, Clock clock) {
        this.queries = queries;
        this.commands = commands;
        this.clock = clock;
    }

    /** 状態の手動更新（S41 / US17 §受入基準 2）。 */
    public record UpdateStatusRequest(
            @NotBlank(message = "新しい状態は必須です") String newStatus,
            String location,
            Instant occurredAt) {
    }

    /**
     * 例外の起票（S43 / US19 §受入基準 1）。
     *
     * <p><b>例外 ID は受け取らない</b>（IT10 レビュー N7）。投影の主キーなので、
     * 呼ぶ側が決めると<b>別の追跡で同じ ID が来たときに投影の insert だけが
     * 落ちる</b>——集約は追跡ごとに別なので気づかず、画面には「起票したのに
     * 一覧に出ない」としか出ない。サーバが採番する。</p>
     *
     * <p>再送で二重に起票されうるが、**同じ内容の例外が 2 件並ぶ**のは
     * 追跡管理者が見て分かる。**投影が黙って落ちる**より扱える。</p>
     */
    public record RegisterExceptionRequest(
            @NotBlank(message = "例外種別は必須です") String exceptionType,
            Instant occurredAt,
            String unLocode,
            @NotBlank(message = "発生状況は必須です") String description) {
    }

    /** 対応の開始（S41 / US19 §受入基準 4）。 */
    public record StartResponseRequest(
            String newEstimatedArrival,
            @NotBlank(message = "対応方針は必須です") String plan) {
    }

    /** 解決（S41 / US19 §受入基準 4）。 */
    public record ResolveExceptionRequest(
            @NotBlank(message = "対応内容は必須です") String resolution) {
    }

    /** 荷主へ知らせた記録（S41 / US19 §受入基準 3）。送信基盤はスコープ外。 */
    public record NotifyShipperRequest(
            @NotBlank(message = "伝えた手段は必須です") String means,
            @NotBlank(message = "伝えた内容は必須です") String summary) {
    }

    /** 一覧が一度に返す上限。**上限を超える指定は切り詰める**（1 行ごとに問い合わせるため）。 */
    private static final int MAX_LIMIT = 200;

    /** 追跡一覧（S40）。荷主なら自社のぶんだけ。 */
    @GetMapping
    public ResponseEntity<TrackingListView> list(
            @RequestHeader(value = "X-Auth-Shipper-Id", required = false) String shipperId,
            @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @RequestParam(defaultValue = "false") boolean includeDelivered,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(queries.query(
                new FindTrackingsQuery(shipperOf(roles, shipperId), includeDelivered,
                        clamped(limit)),
                TrackingListView.class));
    }

    /**
     * 何件まで返すか。<b>負値と極端な大きさを断る</b>。
     *
     * <p>負値はそのまま SQL に渡ると落ちて 500 になり、「壊れた」と読まれる。
     * 極端に大きい値は 1 行ごとの問い合わせを増やすだけで、画面には出せない。</p>
     */
    private static int clamped(int limit) {
        return Math.clamp(limit, 1, MAX_LIMIT);
    }

    /**
     * 絞り込みに使う荷主 ID。<b>荷主を名乗るなら必須</b>。
     *
     * @return 追跡管理者なら {@code null}（全件）、荷主ならその荷主 ID
     * @throws org.springframework.web.server.ResponseStatusException 荷主なのに荷主 ID が無い
     */
    private static String shipperOf(String roles, String shipperId) {
        boolean declaresShipper = roles != null && roles.contains("ROLE_SHIPPER");
        boolean hasShipperId = shipperId != null && !shipperId.isBlank();

        if (declaresShipper && !hasShipperId) {
            // 紐付けが済んでいない荷主。全件を見せるより断るほうが害が小さい。
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "荷主の紐付けがありません。担当者にお問い合わせください");
        }
        // **荷主 ID が来ていれば、ロールが読めなくても絞る。** どちらか一方が
        // 欠けたときに広い側へ倒すと、欠けさせるだけで他社の追跡が見える。
        return hasShipperId ? shipperId : null;
    }

    /**
     * 直近で状態が変わった件数（S02 荷主）。
     *
     * <p>US17 §受入基準 4 の「荷主への通知」は送信基盤がスコープ外。<b>荷主が
     * 自分で気づける手段</b>で代える。</p>
     */
    @GetMapping("/recently-changed")
    public ResponseEntity<RecentlyChangedView> recentlyChanged(
            @RequestHeader(value = "X-Auth-Shipper-Id", required = false) String shipperId,
            @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @RequestParam(defaultValue = "24") int withinHours) {
        String shipper = shipperOf(roles, shipperId);
        if (shipper == null) {
            // 追跡管理者は全社を見るので、この受け皿は要らない（自分の仕事ではない）。
            return ResponseEntity.ok(new RecentlyChangedView(0, withinHours));
        }
        return ResponseEntity.ok(queries.query(
                new CountRecentlyChangedQuery(shipper, withinHours), RecentlyChangedView.class));
    }

    /**
     * 追跡詳細（S41）。
     *
     * <p><b>他社のものは「見つからない」として返す。</b> 権限が無いことを伝えると、
     * その番号が実在することが分かる（公開照会と同じ判断）。</p>
     */
    @GetMapping("/{trackingNumber}")
    public ResponseEntity<TrackingView> find(@PathVariable String trackingNumber,
            @RequestHeader(value = "X-Auth-Shipper-Id", required = false) String shipperId,
            @RequestHeader(value = "X-Auth-Roles", required = false) String roles) {
        TrackingView view = queries.query(
                new FindTrackingQuery(trackingNumber, shipperOf(roles, shipperId)),
                TrackingView.class);
        return view == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }

    /**
     * 状態を手で更新する（S41 / US17 §受入基準 2）。<b>追跡管理者だけ</b>。
     *
     * <p>遷移してよいかは集約が判断する（不変条件 2）。ここでは判定を書き直さない
     * ——書き直すと判定が 2 つになり、片方だけ直る。</p>
     *
     * <p><b>更新者はヘッダから取る。</b> 本文に載せると、他人の名前で記録できる。</p>
     */
    @PostMapping("/{trackingNumber}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable String trackingNumber,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody UpdateStatusRequest request) {
        commands.sendAndWait(new UpdateTransportStatusCommand(trackingNumber,
                statusOf(request.newStatus()), request.location(),
                // 入力されなければ「いま」。後から入れ直すときだけ日時を指定する。
                // **業務の時計で決める**（JVM 既定だと時差の分だけ日付がずれる）。
                request.occurredAt() == null ? clock.instant() : request.occurredAt(),
                username), Void.class);
        return ResponseEntity.noContent().build();
    }

    /** 未解決の例外の一覧（S42 / US19 §受入基準 5）。 */
    @GetMapping("/exceptions")
    public ResponseEntity<ExceptionListView> openExceptions(
            @RequestParam(defaultValue = "false") boolean includeResolved) {
        return ResponseEntity.ok(queries.query(new FindOpenExceptionsQuery(includeResolved),
                ExceptionListView.class));
    }

    /**
     * 例外を起票する（S43 / US19 §受入基準 1・2）。
     *
     * <p><b>起票者はヘッダから取る。</b> 本文に載せると、他人の名前で記録できる。</p>
     */
    @PostMapping("/{trackingNumber}/exceptions")
    public ResponseEntity<RegisteredExceptionResponse> registerException(
            @PathVariable String trackingNumber,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody RegisterExceptionRequest request) {
        // **採番はサーバが持つ**（IT10 レビュー N7）。投影の主キーなので、
        // 呼ぶ側が決めると別の追跡で衝突して投影の insert だけが落ちる。
        String exceptionId = java.util.UUID.randomUUID().toString();
        commands.sendAndWait(new RegisterTrackingExceptionCommand(trackingNumber,
                exceptionId,
                exceptionTypeOf(request.exceptionType()),
                // 入力されなければ「いま」。**業務の時計で決める**。
                request.occurredAt() == null ? clock.instant() : request.occurredAt(),
                request.unLocode(), request.description(), username), Void.class);
        // **採番した ID を返す。** 返さないと、呼ぶ側は起票した例外を名指しで
        // 追えず、一覧から推測することになる。
        return ResponseEntity.ok(new RegisteredExceptionResponse(exceptionId));
    }

    /** 起票の応答。<b>サーバが採番した例外 ID を返す</b>（IT10 レビュー N7）。 */
    public record RegisteredExceptionResponse(String exceptionId) {
    }

    /** 対応を始める（S41 / US19 §受入基準 4）。 */
    @PostMapping("/{trackingNumber}/exceptions/{exceptionId}/response")
    public ResponseEntity<Void> startResponse(@PathVariable String trackingNumber,
            @PathVariable String exceptionId,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody StartResponseRequest request) {
        commands.sendAndWait(new StartExceptionResponseCommand(trackingNumber, exceptionId,
                request.newEstimatedArrival(), request.plan(), username), Void.class);
        return ResponseEntity.noContent().build();
    }

    /** 解決する（S41 / US19 §受入基準 4）。 */
    @PostMapping("/{trackingNumber}/exceptions/{exceptionId}/resolution")
    public ResponseEntity<Void> resolveException(@PathVariable String trackingNumber,
            @PathVariable String exceptionId,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody ResolveExceptionRequest request) {
        commands.sendAndWait(new ResolveTrackingExceptionCommand(trackingNumber, exceptionId,
                request.resolution(), username), Void.class);
        return ResponseEntity.noContent().build();
    }

    /**
     * 荷主へ知らせた事実を記録する（S41 / US19 §受入基準 3）。
     *
     * <p><b>送信基盤はスコープ外</b>（ui_design.md:120）。通知は現行の手作業で行い、
     * ここに残るのは「いつ・どうやって・何を伝えたか」だけである。</p>
     */
    @PostMapping("/{trackingNumber}/exceptions/{exceptionId}/notifications")
    public ResponseEntity<Void> notifyShipper(@PathVariable String trackingNumber,
            @PathVariable String exceptionId,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody NotifyShipperRequest request) {
        commands.sendAndWait(new NotifyShipperOfExceptionCommand(trackingNumber, exceptionId,
                request.means(), request.summary(), username), Void.class);
        return ResponseEntity.noContent().build();
    }

    /**
     * 例外種別の名前を型に直す。<b>知らない名前を 500 にしない</b>（入力の誤り）。
     *
     * <p><b>手で起票してよい種別だけを通す。</b> 誤配は荷役が、税関保留は通関が
     * 決める（{@link ExceptionType#reportableByHand}）。<b>画面が選択肢から
     * 外していても、この経路を直接叩けば通る</b>——IT9 で引取に対して実測した
     * 形をそのまま繰り返さない。自動起票は反応ハンドラがコマンドを直接送るので、
     * ここを通らない。</p>
     */
    private static ExceptionType exceptionTypeOf(String name) {
        ExceptionType type;
        try {
            type = ExceptionType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolation("知らない例外種別です: " + name);
        }
        if (!type.reportableByHand()) {
            throw new BusinessRuleViolation(
                    type.label() + "は荷役と通関の記録からシステムが起票します");
        }
        return type;
    }

    /**
     * 状態の名前を型に直す。
     *
     * <p><b>知らない名前を 500 にしない。</b> {@code valueOf} をそのまま呼ぶと
     * {@code IllegalArgumentException} になり、画面には「壊れた」と出る。実際には
     * 入力が誤っているだけなので、業務の断りとして返す。</p>
     */
    private static TransportStatus statusOf(String name) {
        try {
            return TransportStatus.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolation("知らない状態です: " + name);
        }
    }
}
