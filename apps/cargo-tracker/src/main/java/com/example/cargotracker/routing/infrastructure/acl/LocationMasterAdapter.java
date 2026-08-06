package com.example.cargotracker.routing.infrastructure.acl;

import com.example.cargotracker.routing.application.internal.outboundservices.acl.KnownPorts;
import com.example.cargotracker.routing.infrastructure.repositories.LocationMasterMapper;
import com.example.cargotracker.shared.domain.model.Location;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** {@link KnownPorts} の実装。港マスタ（{@code location}）を読む。 */
@Component
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
        List<String> codes = locations.stream().map(Location::unlocode).distinct().toList();
        Set<String> known = Set.copyOf(mapper.findExisting(codes));
        return codes.stream()
                .filter(code -> !known.contains(code))
                .map(Location::of)
                .toList();
    }
}
