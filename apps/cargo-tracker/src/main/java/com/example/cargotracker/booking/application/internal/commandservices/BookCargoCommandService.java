package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.KnownPorts;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.ShipperExistenceChecker;
import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 貨物予約登録のユースケース（US04）。 */
@Service
public class BookCargoCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.booking");

    private final CargoRepository cargoRepository;
    private final ShipperExistenceChecker shipperExistenceChecker;
    private final KnownPorts knownPorts;

    public BookCargoCommandService(
            CargoRepository cargoRepository,
            ShipperExistenceChecker shipperExistenceChecker,
            KnownPorts knownPorts) {
        this.cargoRepository = cargoRepository;
        this.shipperExistenceChecker = shipperExistenceChecker;
        this.knownPorts = knownPorts;
    }

    /**
     * 予約を登録する。
     *
     * <p>荷主の存在確認は ACL ポート経由で行う（ビジネスルール 9）。
     * **ドメインモデルの中で確認しようとすると BC 間の直接参照になる**ため、
     * 集約の外側であるここが確認の場所になる。
     */
    @Transactional
    public Result book(BookCargoCommand command, String actor) {
        if (!shipperExistenceChecker.exists(command.shipperId())) {
            return Result.shipperNotFound();
        }

        // 港マスタに無い港は、外部キー違反（500）ではなく業務のエラーとして返す。
        // **どの港が悪いのかを示さないと、利用者は直せない**
        List<Location> unknownPorts = knownPorts.findUnknown(List.of(
                command.routeSpecification().origin(),
                command.routeSpecification().destination()));
        if (!unknownPorts.isEmpty()) {
            return Result.unknownPorts(unknownPorts);
        }

        Cargo cargo = Cargo.book(command);
        cargoRepository.save(cargo);

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("貨物予約登録 bookingId={} shipperId={} origin={} destination={} actor={}",
                    cargo.bookingId().value(), cargo.shipperId().value(),
                    cargo.routeSpecification().origin().unlocode(),
                    cargo.routeSpecification().destination().unlocode(),
                    AuditValue.sanitize(actor));
        }

        return Result.booked(cargo);
    }

    /** 登録の結果。 */
    public enum Outcome {
        /** 登録した。 */
        BOOKED,
        /** 指定された荷主が存在しない。 */
        SHIPPER_NOT_FOUND,
        /** 港マスタに登録されていない港が指定された。 */
        UNKNOWN_PORTS
    }

    /**
     * 登録の結果。
     *
     * <p><strong>集約そのものを返さない。</strong> 呼び出し側（画面）が要るのは
     * 「どの予約ができたか」だけである。可変の集約を渡すと、画面から状態を
     * 動かせてしまい、<strong>不変条件を通らない経路ができる</strong>。
     *
     * @param outcome 結果の種別
     * @param bookingId 登録した予約の ID。失敗時は {@code null}
     * @param unknownPorts 港マスタに無かった港（該当しない場合は空）
     */
    public record Result(
            Outcome outcome,
            com.example.cargotracker.booking.domain.model.aggregates.BookingId bookingId,
            List<Location> unknownPorts) {

        public Result {
            unknownPorts = List.copyOf(unknownPorts);
        }

        static Result booked(Cargo cargo) {
            return new Result(Outcome.BOOKED, cargo.bookingId(), List.of());
        }

        static Result shipperNotFound() {
            return new Result(Outcome.SHIPPER_NOT_FOUND, null, List.of());
        }

        static Result unknownPorts(List<Location> ports) {
            return new Result(Outcome.UNKNOWN_PORTS, null, ports);
        }

        public boolean isBooked() {
            return outcome == Outcome.BOOKED;
        }
    }
}
