package com.example.cargotracker.routing.application.internal.queryservices;

import com.example.cargotracker.routing.application.internal.outboundservices.VoyageQueryPort;
import com.example.cargotracker.routing.domain.model.VoyageLeg;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageLegDetailResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 航海区間詳細を検索するクエリサービス（US22 #assignModal 表示用）。
 */
@Service
public class VoyageLegsQueryService {

    private final VoyageQueryPort voyageQueryPort;

    public VoyageLegsQueryService(VoyageQueryPort voyageQueryPort) {
        this.voyageQueryPort = voyageQueryPort;
    }

    public List<VoyageLegDetailResponse> findByVoyageNumber(String voyageNumber) {
        return voyageQueryPort.findByVoyageNumber(voyageNumber)
                .map(voyage -> {
                    List<VoyageLeg> legs = voyage.legs();
                    return IntStream.range(0, legs.size())
                            .mapToObj(i -> {
                                VoyageLeg l = legs.get(i);
                                return new VoyageLegDetailResponse(
                                        l.originLocode(),
                                        l.destinationLocode(),
                                        l.departureDate(),
                                        l.arrivalDate(),
                                        i
                                );
                            })
                            .toList();
                })
                .orElse(List.of());
    }
}
