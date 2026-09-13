package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.domain.model.commands.ApproveCancellationCommand;
import com.example.cargotracker.booking.domain.model.commands.RejectCancellationCommand;
import com.example.cargotracker.booking.domain.model.commands.RequestCancellationCommand;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.CancellationListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries
        .FindCancellationsOfBookingQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries
        .FindPendingCancellationsQuery;
import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 予約のキャンセル（UC22 / US30）。
 *
 * <p><b>{@code BookingController} から分けた。</b> 1 ファイルが 500 行を超えると、
 * 何を受け付けるファイルなのかが読めなくなる。US30 は申請・承認・却下・履歴・
 * 承認待ち一覧で 1 つのまとまりなので、切り口もそこに置いた。</p>
 *
 * <p><b>入口は 1 つ。</b> 輸送開始前は即座にキャンセル、輸送中は申請になる
 * ——どちらになるかは集約が状態から決める。画面が出し分けると、同じ判断が
 * 2 か所に住む。</p>
 */
@RestController
@RequestMapping("/api/v1/booking/bookings")
public class CancellationController {

    private final CommandGateway commands;
    private final QueryDispatcher queries;

    public CancellationController(CommandGateway commands, QueryDispatcher queries) {
        this.commands = commands;
        this.queries = queries;
    }

    /**
     * キャンセルを申し出る（US30 §受入基準 1・2・3）。
     *
     * <p><b>識別子はサーバで採る。</b> 画面に採らせると、押し直しが二重の申請に
     * なる。<b>36 文字に収める</b>——列は {@code VARCHAR(36)} で、接頭辞 + UUID を
     * そのまま繋ぐとあふれ、投影だけが静かに退避される。</p>
     */
    @PostMapping("/{bookingId}/cancellation")
    public ResponseEntity<Void> request(@PathVariable String bookingId,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody CancellationRequest request) {
        String requestId = "CR-" + java.util.UUID.randomUUID().toString().replace("-", "");
        commands.sendAndWait(new RequestCancellationCommand(bookingId, requestId,
                request.reason(), username), Void.class);
        return ResponseEntity.ok().build();
    }

    /** 申請の入力。理由は必須（§受入基準 3）。 */
    public record CancellationRequest(@NotBlank String reason) {
    }

    /**
     * 申請を承認する（US30 §受入基準 5・6）。<b>追跡管理者の操作</b>。
     *
     * <p><b>承認とは「どこで降ろすか」を決めること</b>である。</p>
     */
    @PostMapping("/{bookingId}/cancellation/approval")
    public ResponseEntity<Void> approve(@PathVariable String bookingId,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody ApprovalRequest request) {
        commands.sendAndWait(new ApproveCancellationCommand(bookingId,
                request.dischargeUnLocode(), request.reason(), username), Void.class);
        return ResponseEntity.ok().build();
    }

    /**
     * 承認の入力。
     *
     * @param reason 任意（陸揚げ地が理由を語る）
     */
    public record ApprovalRequest(@NotBlank String dischargeUnLocode, String reason) {
    }

    /** 申請を却下する（US30 §受入基準 7）。<b>予約の状態は動かない</b>。 */
    @PostMapping("/{bookingId}/cancellation/rejection")
    public ResponseEntity<Void> reject(@PathVariable String bookingId,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody RejectionRequest request) {
        commands.sendAndWait(new RejectCancellationCommand(bookingId, request.reason(),
                username), Void.class);
        return ResponseEntity.ok().build();
    }

    /** 却下の入力。理由は必須——申請した営業が次に何をすればよいか決められない。 */
    public record RejectionRequest(@NotBlank String reason) {
    }

    /**
     * 承認待ちの申請（S23 / US30 §受入基準 4）。
     *
     * <p><b>`/{bookingId}/cancellation` より先に宣言する。</b> `cancellations` が
     * 予約 ID として読まれると、承認待ちの一覧が空で返る。</p>
     */
    @GetMapping("/cancellations")
    public ResponseEntity<CancellationListView> pending() {
        return ResponseEntity.ok(queries.query(new FindPendingCancellationsQuery(),
                CancellationListView.class));
    }

    /** その予約のキャンセル履歴（S22 / US30 §受入基準 10）。 */
    @GetMapping("/{bookingId}/cancellation")
    public ResponseEntity<CancellationListView> history(@PathVariable String bookingId) {
        return ResponseEntity.ok(queries.query(new FindCancellationsOfBookingQuery(bookingId),
                CancellationListView.class));
    }
}
