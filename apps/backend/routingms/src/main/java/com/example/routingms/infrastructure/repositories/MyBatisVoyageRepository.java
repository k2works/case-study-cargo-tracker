package com.example.routingms.infrastructure.repositories;

import com.example.routingms.application.port.VoyageRepository;
import com.example.routingms.application.port.VoyageSearchCriteria;
import com.example.routingms.domain.model.CargoType;
import com.example.routingms.domain.model.CarrierMovement;
import com.example.routingms.domain.model.RouteSearchSpecification;
import com.example.routingms.domain.model.Schedule;
import com.example.routingms.domain.model.Voyage;
import com.example.routingms.domain.model.VoyageNumber;
import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MyBatisVoyageRepository implements VoyageRepository {

    private final VoyageMapper mapper;

    public MyBatisVoyageRepository(VoyageMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 登録・更新のどちらも受ける。
     *
     * <p>区間は入れ替えを消してから入れ直す。差分で更新すると順序（seq_number）の
     * 付け替えが要り、途中で失敗したときに「つながっていない航海」が残る。
     */
    @Override
    @Transactional
    public Voyage save(Voyage voyage) {
        VoyageRecord row = new VoyageRecord();
        row.setVoyageNumber(voyage.voyageNumber().value());
        row.setVesselName(voyage.vesselName());
        row.setCarrierName(voyage.carrierName());
        row.setSupportedCargoTypes(joinCargoTypes(voyage.supportedCargoTypes()));

        VoyageRecord existing = mapper.findByVoyageNumber(row.getVoyageNumber());
        if (existing == null) {
            mapper.insertVoyage(row);
        } else {
            row.setId(existing.getId());
            mapper.updateVoyage(row);
            mapper.deleteMovements(row.getId());
        }

        int seq = 1;
        for (CarrierMovement movement : voyage.schedule().carrierMovements()) {
            CarrierMovementRecord movementRow = new CarrierMovementRecord();
            movementRow.setVoyageId(row.getId());
            movementRow.setDepartureLocationUnlocode(movement.departureLocation().unLocode());
            movementRow.setArrivalLocationUnlocode(movement.arrivalLocation().unLocode());
            movementRow.setDepartureDate(movement.departureTime());
            movementRow.setArrivalDate(movement.arrivalTime());
            movementRow.setSeqNumber(seq++);
            mapper.insertMovement(movementRow);
        }

        return findByVoyageNumber(voyage.voyageNumber()).orElseThrow(() ->
                new IllegalStateException(
                        "保存した航海を読み戻せません: " + voyage.voyageNumber().value()));
    }

    @Override
    public Optional<Voyage> findByVoyageNumber(VoyageNumber voyageNumber) {
        return Optional.ofNullable(mapper.findByVoyageNumber(voyageNumber.value()))
                .map(this::toDomain);
    }

    @Override
    public List<Voyage> search(VoyageSearchCriteria criteria, int limit) {
        return mapper.search(toQueryParameters(criteria), limit).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public int countMatching(VoyageSearchCriteria criteria) {
        return mapper.countMatching(toQueryParameters(criteria));
    }

    /**
     * 経路探索の対象を引く（US08）。
     *
     * <p>出発地・目的地では絞らない。積み替えのある経路は、出発地にも目的地にも寄らない
     * 航海を途中で使う。ここで港を絞ると、その経路がまるごと候補から消える。
     *
     * <p>落とすのは「その貨物種別を運べない航海」「期限より後にしか出ない航海」、そして
     * <strong>すでに出てしまった航海</strong>だけである。運べるか・順序が合うかは集約が判定する。
     */
    @Override
    public List<Voyage> findCandidates(RouteSearchSpecification specification,
            Instant notDepartedBefore) {
        // **再設計では期限で落とさない**（US28-4・[ADR-026] 決定 4）。集約から期限検査を
        // 外し、探索の枝刈りも条件に従わせても、ここで「期限より後に出る航海」を落とせば
        // 候補は組み上がらない——**絞りは 3 か所にあり、1 か所でも残っていれば効かない**
        Instant departureTo = specification.enforcesDeadline()
                ? specification.arrivalDeadline()
                : null;
        VoyageSearchCriteria criteria = new VoyageSearchCriteria(
                null, null, notDepartedBefore, departureTo, specification.cargoType());
        return mapper.searchAll(toQueryParameters(criteria)).stream()
                .map(this::toDomain)
                .toList();
    }

    /**
     * 検索条件を SQL に渡せる形にする。
     *
     * <p>貨物種別は列に入っている文字列と突き合わせるため、名前で渡す。
     */
    private VoyageSearchParameters toQueryParameters(VoyageSearchCriteria criteria) {
        return new VoyageSearchParameters(
                criteria.originUnLocode(), criteria.destinationUnLocode(),
                criteria.departureFrom(), criteria.departureTo(),
                criteria.cargoType() == null ? null : criteria.cargoType().name());
    }

    private Voyage toDomain(VoyageRecord row) {
        List<CarrierMovement> movements = mapper.findMovements(row.getId()).stream()
                .map(movement -> CarrierMovement.restore(
                        Location.of(movement.getDepartureLocationUnlocode(),
                                movement.getDepartureLocationName()),
                        Location.of(movement.getArrivalLocationUnlocode(),
                                movement.getArrivalLocationName()),
                        movement.getDepartureDate(), movement.getArrivalDate()))
                .toList();

        return Voyage.restore(row.getId(), VoyageNumber.restore(row.getVoyageNumber()),
                row.getVesselName(), row.getCarrierName(),
                parseCargoTypes(row.getSupportedCargoTypes()), Schedule.restore(movements));
    }

    private String joinCargoTypes(Set<CargoType> cargoTypes) {
        // 並びを固定する。順序が揺れると、内容が同じでも更新のたびに行が変わったように見える
        return Arrays.stream(CargoType.values())
                .filter(cargoTypes::contains)
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    /**
     * 保存された文字列から復元する。ここでは検査しない。
     *
     * <p>読めない値は落とす。例外にすると、値が 1 つ古いだけでその航海の行が開けなくなる。
     */
    private Set<CargoType> parseCargoTypes(String stored) {
        if (stored == null || stored.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(stored.split(","))
                .map(String::trim)
                .map(name -> Arrays.stream(CargoType.values())
                        .filter(cargoType -> cargoType.name().equals(name))
                        .findFirst())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
