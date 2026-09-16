package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.domain.model.commands.RegisterShipperCommand;
import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import com.example.cargotracker.booking.domain.model.valueobjects.CorporateContract;
import com.example.cargotracker.booking.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.booking.domain.model.valueobjects.Email;
import com.example.cargotracker.booking.domain.model.valueobjects.ShipperType;
import com.example.cargotracker.booking.infrastructure.query.ShipperQueries.ExistsShipperEmailQuery;
import com.example.cargotracker.booking.infrastructure.query.ShipperQueries.FindShipperQuery;
import com.example.cargotracker.booking.infrastructure.query.ShipperQueries.FindShippersQuery;
import com.example.cargotracker.booking.infrastructure.query.ShipperQueries.ShipperListView;
import com.example.cargotracker.booking.infrastructure.query.ShipperQueries.ShipperView;
import com.example.cargotracker.booking.interfaces.rest.dto.ShipperDtos.PendingResponse;
import com.example.cargotracker.booking.interfaces.rest.dto.ShipperDtos.RegisterShipperRequest;
import com.example.cargotracker.booking.interfaces.rest.dto.ShipperDtos.RegisterShipperResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 荷主（UC02 / US02）。 */
@RestController
@RequestMapping("/api/v1/booking/shippers")
public class ShipperController {

    /**
     * シミュレーション由来の印を受け付けるか（US33 §受入基準 3 / [ADR-0020]）。
     *
     * <p><b>印は業務の一覧から荷主と貨物を消す。</b> 誰でも立てられると、本物の
     * 荷主を「シミュレーション由来」として静かに見えなくできる——営業の一覧にも
     * 経理の一覧にも出なくなり、消えたことに誰も気づかない。</p>
     *
     * <p><b>許可した環境でだけ受け付ける。</b> 既定は無効で、本番では立てない。
     * ロールで絞らないのは、シミュレーションが<b>人と同じ利用者</b>（営業担当者）
     * として登録するからである（[ADR-0020] 決定 2）——ロールで分けると、
     * シミュレーションだけが通る道を作ることになる。</p>
     */
    @Value("${cargo-tracker.simulation.enabled:false}")
    private boolean simulationEnabled;


    private final CommandGateway commandGateway;
    private final QueryDispatcher queries;
    public ShipperController(CommandGateway commandGateway, QueryDispatcher queries) {
        this.commandGateway = commandGateway;
        this.queries = queries;
    }

    @PostMapping
    public ResponseEntity<RegisterShipperResponse> register(
            @Valid @RequestBody RegisterShipperRequest request) {
        Email email = new Email(request.email());

        // 一意の三段の 1 段目。断定ではなく問いかけなので、利用者が「続ける」と
        // 答えていれば通す。同時登録のレースでは素通りするため、2 段目（投影の
        // UNIQUE）と 3 段目（要確認一覧）が本当の砦になる。
        if (!request.duplicateAcknowledged()
                && Boolean.TRUE.equals(queries.query(new ExistsShipperEmailQuery(email.value()), Boolean.class))) {
            throw new DuplicateShipperEmailException(email.value());
        }

        if (request.simulatedOrigin() && !simulationEnabled) {
            // **黙って落とさない。** 印が付かないまま登録されると、
            // シミュレーションの荷主が業務の一覧に混ざる。
            throw new BusinessRuleViolation(
                    "この環境ではシミュレーション由来の荷主を登録できません"
                            + "（印の付いた荷主は業務の一覧に出ないため）");
        }

        String shipperId = UUID.randomUUID().toString();
        ShipperType type = ShipperType.valueOf(request.shipperType());

        // 平文のまま送る。暗号化はイベントのシリアライズ時に行う（ADR-0003 決定 1）。
        // ここで暗号化すると、暗号文が Email の形式検査に落ちる。
        commandGateway.sendAndWait(new RegisterShipperCommand(
                shipperId,
                request.name(),
                type,
                email,
                request.phone(),
                request.address(),
                corporateContract(request),
                // **印は入口で受ける。** 内部に専用の書き込み経路を作ると、
                // シミュレーションだけが通る道ができて、実際の操作の壊れに
                // 気づけなくなる（[ADR-0020] 決定 2）。
                request.simulatedOrigin()));

        return ResponseEntity.created(URI.create("/api/v1/booking/shippers/" + shipperId))
                .body(new RegisterShipperResponse(shipperId));
    }

    /**
     * 契約情報を組み立てる。
     *
     * <p><b>種別で捨てない。</b> 個人を選んだまま契約番号が送られてきたら、そのまま
     * 集約へ渡して断らせる。入口で黙って落とすと「登録できたのに割引が効かない」
     * という形で後から出る。不変条件は集約が持つ（domain-model.md「Shipper 集約」）。</p>
     */
    private static CorporateContract corporateContract(RegisterShipperRequest request) {
        if (request.contractNumber() == null && request.discountRate() == null) {
            return null;
        }
        return new CorporateContract(request.contractNumber(),
                new DiscountRate(request.discountRate()));
    }

    /**
     * 荷主 1 件。投影がまだなら {@code 202} を返す。
     *
     * <p>{@code 404} にすると「登録に失敗した」と読めてしまう。受け付けたことと
     * 反映が終わったことは別なので、画面が「反映中」を出せるように区別する。</p>
     */
    @GetMapping("/{shipperId}")
    public ResponseEntity<?> find(@PathVariable String shipperId) {
        ShipperView view = queries.query(new FindShipperQuery(shipperId), ShipperView.class);
        if (view == null) {
            return ResponseEntity.accepted()
                    .body(new PendingResponse(shipperId, "登録を受け付けました。反映までしばらくお待ちください"));
        }
        return ResponseEntity.ok(view);
    }

    @GetMapping
    public ShipperListView list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String q) {
        return queries.query(new FindShippersQuery(page, size, q), ShipperListView.class);
    }

    /** メールアドレスが既に使われている（409）。 */
    public static class DuplicateShipperEmailException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public DuplicateShipperEmailException(String email) {
            super("このメールアドレスは既に登録されています: " + email);
        }
    }
}
