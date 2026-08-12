package com.example.cargotracker.booking.infrastructure.acl;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.KnownPorts;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import com.example.cargotracker.shared.infrastructure.repositories.LocationMasterMapper;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** {@link KnownPorts} の実装。港マスタ（{@code location}）を読む。 */
@Component("bookingLocationMasterAdapter")
public class LocationMasterAdapter implements KnownPorts {

    private final LocationMasterMapper mapper;

    public LocationMasterAdapter(LocationMasterMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Location> findUnknown(Collection<Location> locations) {
        if (locations.isEmpty()) {
            return List.of();
        }
        // **1 件ずつ問い合わせない。** 出発地と目的地をまとめて 1 回で確かめる
        List<String> codes = locations.stream().map(Location::unlocode).distinct().toList();
        Set<String> known = Set.copyOf(mapper.findExisting(codes));
        return codes.stream()
                .filter(code -> !known.contains(code))
                .map(Location::of)
                .toList();
    }
}
