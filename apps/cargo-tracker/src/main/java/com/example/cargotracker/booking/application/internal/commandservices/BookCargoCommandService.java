package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.ShipperExistenceChecker;
import com.example.cargotracker.booking.domain.model.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
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

    public BookCargoCommandService(
            CargoRepository cargoRepository, ShipperExistenceChecker shipperExistenceChecker) {
        this.cargoRepository = cargoRepository;
        this.shipperExistenceChecker = shipperExistenceChecker;
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

        Cargo cargo = Cargo.book(command);
        cargoRepository.save(cargo);

        AUDIT.info("貨物予約登録 bookingId={} shipperId={} origin={} destination={} actor={}",
                cargo.bookingId().value(), cargo.shipperId().value(),
                cargo.routeSpecification().origin().unlocode(),
                cargo.routeSpecification().destination().unlocode(),
                AuditValue.sanitize(actor));

        return Result.booked(cargo);
    }

    /** 登録の結果。 */
    public enum Outcome {
        /** 登録した。 */
        BOOKED,
        /** 指定された荷主が存在しない。 */
        SHIPPER_NOT_FOUND
    }

    /**
     * 登録の結果。
     *
     * @param outcome 結果の種別
     * @param cargo   登録した貨物。失敗時は {@code null}
     */
    public record Result(Outcome outcome, Cargo cargo) {

        static Result booked(Cargo cargo) {
            return new Result(Outcome.BOOKED, cargo);
        }

        static Result shipperNotFound() {
            return new Result(Outcome.SHIPPER_NOT_FOUND, null);
        }

        public boolean isBooked() {
            return outcome == Outcome.BOOKED;
        }
    }
}
