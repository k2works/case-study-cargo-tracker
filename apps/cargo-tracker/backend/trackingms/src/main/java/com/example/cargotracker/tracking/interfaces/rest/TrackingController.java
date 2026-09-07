package com.example.cargotracker.tracking.interfaces.rest;

import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import com.example.cargotracker.tracking.domain.model.commands.UpdateTransportStatusCommand;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.FindTrackingQuery;
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
 * 見えてしまう。<b>ロールも同じくヘッダから読む</b>——追跡管理者には荷主 ID が
 * 付かないので、付いていない要求を「全件」として扱う。</p>
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

    /** 追跡一覧（S40）。荷主 ID が付いていれば自社のぶんだけ。 */
    @GetMapping
    public ResponseEntity<TrackingListView> list(
            @RequestHeader(value = "X-Auth-Shipper-Id", required = false) String shipperId,
            @RequestParam(defaultValue = "false") boolean includeDelivered,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(queries.query(
                new FindTrackingsQuery(shipperId, includeDelivered, limit),
                TrackingListView.class));
    }

    /**
     * 追跡詳細（S41）。
     *
     * <p><b>他社のものは「見つからない」として返す。</b> 権限が無いことを伝えると、
     * その番号が実在することが分かる（公開照会と同じ判断）。</p>
     */
    @GetMapping("/{trackingNumber}")
    public ResponseEntity<TrackingView> find(@PathVariable String trackingNumber,
            @RequestHeader(value = "X-Auth-Shipper-Id", required = false) String shipperId) {
        TrackingView view = queries.query(
                new FindTrackingQuery(trackingNumber, shipperId), TrackingView.class);
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
