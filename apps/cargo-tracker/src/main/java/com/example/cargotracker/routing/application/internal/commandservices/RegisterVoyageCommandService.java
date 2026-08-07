package com.example.cargotracker.routing.application.internal.commandservices;

import com.example.cargotracker.routing.application.internal.outboundservices.acl.KnownPorts;
import com.example.cargotracker.routing.domain.model.CarrierMovement;
import com.example.cargotracker.routing.domain.model.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.repository.VoyageRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import com.example.cargotracker.shared.domain.model.Location;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 航海スケジュール登録のユースケース（US24）。 */
@Service
public class RegisterVoyageCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.routing");

    private final VoyageRepository repository;
    private final KnownPorts knownPorts;

    public RegisterVoyageCommandService(VoyageRepository repository, KnownPorts knownPorts) {
        this.repository = repository;
        this.knownPorts = knownPorts;
    }

    /**
     * 航海スケジュールを登録する。
     *
     * <p>同一航海番号が既にある場合は登録しない（US24 の受入基準）。
     * <strong>UNIQUE 制約違反をそのまま伝播させると 500 になる。</strong>
     * 同時登録で事前チェックをすり抜けた場合も業務の結果に落とす（IT2 の C6 と同型）。
     */
    @Transactional
    public Result register(RegisterVoyageCommand command, String actor) {
        if (repository.existsByVoyageNumber(command.voyageNumber())) {
            return Result.duplicated();
        }

        // **外部キー違反を 500 にしない。** どの港が登録されていないかを業務の結果で返す
        List<Location> unknown = knownPorts.findUnknown(collectLocations(command));
        if (!unknown.isEmpty()) {
            return Result.unknownPorts(unknown);
        }

        Voyage voyage = Voyage.register(command);
        try {
            repository.save(voyage);
        } catch (DuplicateKeyException e) {
            return Result.duplicated();
        }

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("航海スケジュール登録 voyageNumber={} origin={} destination={} actor={}",
                    AuditValue.sanitize(voyage.voyageNumber().value()),
                    voyage.origin().unlocode(),
                    voyage.destination().unlocode(),
                    AuditValue.sanitize(actor));
        }
        return Result.registered(voyage);
    }

    private static Set<Location> collectLocations(RegisterVoyageCommand command) {
        Set<Location> locations = new LinkedHashSet<>();
        for (CarrierMovement movement : command.schedule().carrierMovements()) {
            locations.add(movement.departureLocation());
            locations.add(movement.arrivalLocation());
        }
        return locations;
    }

    /** 登録の結果。 */
    public enum Outcome {
        /** 登録した。 */
        REGISTERED,
        /** 同一航海番号が既に登録されている。 */
        DUPLICATED,
        /** 港マスタに無い港が含まれている。 */
        UNKNOWN_PORTS
    }

    /**
     * 登録の結果。
     *
     * @param outcome      結果の種別
     * @param voyage       登録した航海。失敗時は {@code null}
     * @param unknownPorts 港マスタに無かった港
     */
    public record Result(Outcome outcome, Voyage voyage, List<Location> unknownPorts) {

        public Result {
            unknownPorts = List.copyOf(unknownPorts);
        }

        static Result registered(Voyage voyage) {
            return new Result(Outcome.REGISTERED, voyage, List.of());
        }

        static Result duplicated() {
            return new Result(Outcome.DUPLICATED, null, List.of());
        }

        static Result unknownPorts(List<Location> ports) {
            return new Result(Outcome.UNKNOWN_PORTS, null, ports);
        }

        public boolean isRegistered() {
            return outcome == Outcome.REGISTERED;
        }
    }
}
