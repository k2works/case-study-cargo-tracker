package com.example.cargotracker.handling.interfaces.rest;

import com.example.cargotracker.handling.domain.model.commands.RegisterCustomsDeclarationCommand;
import com.example.cargotracker.handling.domain.model.commands.UpdateCustomsStatusCommand;
import com.example.cargotracker.handling.domain.model.valueobjects.CustomsStatus;
import com.example.cargotracker.handling.infrastructure.persistence.CargoSnapshotMapper;
import com.example.cargotracker.handling.infrastructure.persistence.CustomsDeclarationMapper;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CustomsDeclarationListView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CustomsDeclarationView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CustomsHistoryView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCustomsDeclarationQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCustomsDeclarationsQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCustomsHistoryQuery;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
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
 * 通関申告（S52・S53 / UC21・US29）。
 *
 * <p><b>集約から見えない不変条件をここで守る</b>——1 申告 1 集約なので、集約は
 * 他の申告を知らない。「未決着は貨物あたり高々 1 件」（不変条件 3）は、読み取り
 * モデルを引けるこの層で判定する。荷役の「5 分以内の同じ記録」（IT9）と同じ形である。</p>
 *
 * <p><b>輸入通関だけを扱う</b>（不変条件 5）。<b>検査で守るのではなく、港を受け取らない
 * ことで守る</b>——申告は貨物 1 件に対して出すもので、どの港かは目的港に決まっている。
 * 港を入力にすると「輸出港での申告」が表せてしまい、そのとき不変条件 3 の
 * 「貨物あたり高々 1 件」が成り立たなくなる。</p>
 */
@RestController
@RequestMapping("/api/v1/handling/customs-declarations")
public class CustomsController {

    private final CommandGateway commands;
    private final QueryDispatcher queries;
    private final CargoSnapshotMapper cargos;
    private final CustomsDeclarationMapper declarations;

    public CustomsController(CommandGateway commands, QueryDispatcher queries,
            CargoSnapshotMapper cargos, CustomsDeclarationMapper declarations) {
        this.commands = commands;
        this.queries = queries;
        this.cargos = cargos;
        this.declarations = declarations;
    }

    /** 通関申告を登録する（US29 §受入基準 1）。 */
    @PostMapping
    public ResponseEntity<Void> register(
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody RegisterRequest request) {
        var cargo = cargos.findByTrackingNumber(request.trackingNumber());
        if (cargo == null) {
            throw new BusinessRuleViolation(
                    "追跡番号 " + request.trackingNumber() + " の貨物が見つかりません");
        }
        rejectSecondUnsettled(request.trackingNumber());

        commands.sendAndWait(new RegisterCustomsDeclarationCommand(
                request.declarationNumber(), request.trackingNumber(), cargo.bookingId(),
                request.declaredAt(), username), String.class);

        return ResponseEntity.created(URI.create("/api/v1/handling/customs-declarations/"
                + request.declarationNumber())).build();
    }

    /**
     * 未決着の申告が既にあれば断る（不変条件 3）。
     *
     * <p><b>集約では守れないのでここに置く。</b> 未決着の申告が同じ貨物に 2 件あると、
     * 引取のガードがどちらを見るかで結果が変わる。{@code REJECTED} の後は出し直せて、
     * {@code CLEARED} の後は<b>もう申告する必要が無い</b>——どちらも決着しているので、
     * ここでは未決着だけを見る。</p>
     */
    private void rejectSecondUnsettled(String trackingNumber) {
        var unsettled = declarations.findUnsettledByCargo(trackingNumber);
        if (!unsettled.isEmpty()) {
            throw new BusinessRuleViolation("追跡番号 " + trackingNumber
                    + " には決着していない通関申告（" + unsettled.getFirst().declarationNumber()
                    + "）があります。先にその申告の状態を更新してください");
        }
    }

    /** 通関状態を更新する（US29 §受入基準 2）。 */
    @PostMapping("/{declarationNumber}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable String declarationNumber,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody UpdateStatusRequest request) {
        commands.sendAndWait(new UpdateCustomsStatusCommand(declarationNumber,
                statusOf(request.status()), request.reason(), username), Void.class);
        return ResponseEntity.ok().build();
    }

    /** 一覧（S52 / US29 §受入基準 7）。**既定で通関済を外す。** */
    @GetMapping
    public ResponseEntity<CustomsDeclarationListView> list(
            @RequestParam(defaultValue = "false") boolean includeCleared,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean overdueOnly) {
        return ResponseEntity.ok(queries.query(new FindCustomsDeclarationsQuery(
                        includeCleared, trackingNumber,
                        status == null ? null : statusOf(status).name(), overdueOnly),
                CustomsDeclarationListView.class));
    }

    /** 申告 1 件（S53）。 */
    @GetMapping("/{declarationNumber}")
    public ResponseEntity<CustomsDeclarationView> find(@PathVariable String declarationNumber) {
        CustomsDeclarationView view = queries.query(
                new FindCustomsDeclarationQuery(declarationNumber),
                CustomsDeclarationView.class);
        return view == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }

    /** 状態の変更履歴（S53 / US29 §受入基準 8）。**Event Store から読む。** */
    @GetMapping("/{declarationNumber}/history")
    public ResponseEntity<CustomsHistoryView> history(@PathVariable String declarationNumber) {
        return ResponseEntity.ok(queries.query(
                new FindCustomsHistoryQuery(declarationNumber), CustomsHistoryView.class));
    }

    /**
     * 画面の言葉を列挙へ。<b>知らない値は入力の誤りとして断る</b>（壊れたのではない）。
     */
    private static CustomsStatus statusOf(String status) {
        try {
            return CustomsStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new BusinessRuleViolation("知らない通関状態です: " + status);
        }
    }

    /**
     * 登録の入力。
     *
     * <p><b>申告番号の書式は検査しない</b>（不変条件 1）。採番するのは税関で、
     * 国ごとに違うものを、こちらの想像で縛らない。空だけを断る。</p>
     */
    public record RegisterRequest(
            @NotBlank(message = "申告番号は必須です") String declarationNumber,
            @NotBlank(message = "追跡番号は必須です") String trackingNumber,
            Instant declaredAt) {
    }

    /** 状態更新の入力。<b>理由は必須</b>（不変条件 2）。 */
    public record UpdateStatusRequest(
            @NotBlank(message = "通関状態は必須です") String status,
            @NotBlank(message = "通関状態の更新には理由が必要です") String reason) {
    }
}
