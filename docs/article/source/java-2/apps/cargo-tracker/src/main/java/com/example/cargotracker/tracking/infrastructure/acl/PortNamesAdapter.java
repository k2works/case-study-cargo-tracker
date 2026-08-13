package com.example.cargotracker.tracking.infrastructure.acl;

import com.example.cargotracker.shared.infrastructure.repositories.LocationMasterMapper;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.PortNames;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** {@link PortNames} の実装。港マスタ（{@code location}）を読む。 */
@Component
public class PortNamesAdapter implements PortNames {

    private final LocationMasterMapper mapper;

    public PortNamesAdapter(LocationMasterMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Map<String, String> findNames(List<String> unlocodes) {
        if (unlocodes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        mapper.findNames(unlocodes).forEach((code, row) ->
                names.put(code, String.valueOf(row.get("name"))));
        return names;
    }
}
