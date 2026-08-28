package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.repository.CargoRepository;
import com.example.shared.auth.AuthenticatedUser;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 追跡番号から貨物を引く（US15-1・[ADR-023] 決定 2）。
 *
 * <p>荷役作業員は予約番号を知らない。手元にあるのは<strong>貨物に貼られた追跡番号</strong>
 * である。この入口が無いと、荷役の記録は始められない。
 *
 * <p>予約の入口（{@link CargoBookingController}）から分けたのは、<strong>相手が人ではない</strong>
 * からである。呼ぶのは handlingms であり、認可も、返す内容も、変わる理由も違う。
 *
 * <p><strong>参照のみで副作用が無い。</strong>書き込みを伴う操作を同じ検査で守ってはいけない。
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class CargoLookupController {

    /**
     * この入口を呼んでよいサービス。
     *
     * <p><strong>名簿に無い主体は通さない。</strong>「システムらしい名前なら通す」形にすると、
     * 載せ忘れた主体ほど素通りする（[ADR-015] 以来の許可リスト方式）。
     *
     * <p>人のロールでは開かない。荷主の連絡先も貨物の内容も返さないとはいえ、
     * <strong>追跡番号を順に試せば実在する予約が分かる</strong>。人が使う入口は
     * 予約詳細（US18・IT8）で、そちらは荷主との紐付けで絞る。
     */
    private static final Set<String> TRUSTED_SERVICE_PRINCIPALS = Set.of("system:handlingms");
    private static final Set<String> TRUSTED_SHIPPER_SNAPSHOT_PRINCIPALS =
            Set.of("system:trackingms");

    private final CargoRepository cargoes;

    public CargoLookupController(CargoRepository cargoes) {
        this.cargoes = cargoes;
    }

    /**
     * 追跡番号で貨物を引く。
     *
     * <p>見つからない追跡番号と、呼んでよくない主体は<strong>区別して返す</strong>。
     * 相手はサービスであり、403 と 404 を打ち分けても実在の手がかりにはならない
     * （人が総当たりする入口ではない）。むしろ同じにすると、配線の誤りが
     * 「番号が無い」に見えて原因が消える。
     */
    @GetMapping("/by-tracking-number/{trackingNumber}")
    public CargoSnapshotResponse byTrackingNumber(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @PathVariable String trackingNumber) {
        requireTrustedService(userId);

        return cargoes.findByTrackingNumber(trackingNumber)
                .map(summary -> CargoSnapshotResponse.from(summary.cargo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "指定された追跡番号の貨物が見つかりません"));
    }

    /**
     * 荷主境界の判定に要る Snapshot を返す（US33）。
     *
     * <p>trackingms だけに開く。人や他サービスに荷主 ID を返す必要はない。
     */
    @GetMapping("/shipper-snapshots/{trackingNumber}")
    public ShipperCargoSnapshotResponse shipperSnapshot(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @PathVariable String trackingNumber) {
        requireTrustedShipperSnapshotService(userId);

        return cargoes.findByTrackingNumber(trackingNumber)
                .map(summary -> ShipperCargoSnapshotResponse.from(summary.cargo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "指定された追跡番号の貨物が見つかりません"));
    }

    private void requireTrustedService(String userId) {
        if (!AuthenticatedUser.of(userId, null).isOneOf(TRUSTED_SERVICE_PRINCIPALS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    private void requireTrustedShipperSnapshotService(String userId) {
        if (!AuthenticatedUser.of(userId, null).isOneOf(TRUSTED_SHIPPER_SNAPSHOT_PRINCIPALS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }
}
