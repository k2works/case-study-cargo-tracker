package com.example.cargotracker.estimation.infrastructure.acl;

import com.example.cargotracker.estimation.application.internal.outboundservices.acl.KnownPorts;
import com.example.cargotracker.shared.infrastructure.repositories.LocationMasterMapper;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** {@link KnownPorts} の実装。港マスタ（{@code location}）を読む。 */
// **BC ごとに同名のアダプタがある。** 単純名が Bean 名になるため明示する
// （booking も同じ形。routing だけが既定名を使っている）
@Component("estimationLocationMasterAdapter")
public class LocationMasterAdapter implements KnownPorts {

    private final LocationMasterMapper mapper;

    public LocationMasterAdapter(LocationMasterMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<String> findUnknown(Collection<String> unlocodes) {
        if (unlocodes.isEmpty()) {
            return List.of();
        }
        List<String> codes = unlocodes.stream().distinct().toList();
        Set<String> known = Set.copyOf(mapper.findExisting(codes));
        return codes.stream().filter(code -> !known.contains(code)).toList();
    }
}
