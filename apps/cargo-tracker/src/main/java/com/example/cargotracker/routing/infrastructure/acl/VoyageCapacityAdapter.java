package com.example.cargotracker.routing.infrastructure.acl;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.VoyageCapacityPort;
import com.example.cargotracker.routing.domain.model.RoutingWeight;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.VoyageNumber;
import com.example.cargotracker.routing.domain.repository.VoyageRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link VoyageCapacityPort} の実装（ACL のアダプタ）。
 *
 * <p><strong>判断は集約が行う。</strong> ここでするのは、素の値を Routing の
 * ことばへ翻訳し、{@code Voyage} に空きの有無を尋ね、結果を素の値で返すことだけである。
 *
 * <p><strong>この貨物自身の重量は数えから除く。</strong> 除かないと、割り当て済みの
 * 貨物を確定するときに自分の重量を二重に数え、空きがあるのに「満船」と判定する。
 */
@Component
public class VoyageCapacityAdapter implements VoyageCapacityPort {

    private final VoyageRepository voyageRepository;

    public VoyageCapacityAdapter(VoyageRepository voyageRepository) {
        this.voyageRepository = voyageRepository;
    }

    @Override
    public List<String> findFullVoyages(
            List<String> voyageNumbers, BigDecimal weightKilograms, String excludeBookingId) {
        if (voyageNumbers.isEmpty()) {
            return List.of();
        }
        List<VoyageNumber> numbers = voyageNumbers.stream().distinct()
                .map(VoyageNumber::new).toList();
        // **境界は素の値（文字列）で受け取り、ここで Routing のことばに直す。**
        // ACL の役目はまさにこの翻訳である
        Map<VoyageNumber, RoutingWeight> assigned = voyageRepository.findAssignedWeights(
                numbers,
                excludeBookingId == null ? null : java.util.UUID.fromString(excludeBookingId));

        RoutingWeight required = RoutingWeight.ofKilograms(weightKilograms);
        List<String> full = new ArrayList<>();
        for (VoyageNumber number : numbers) {
            Voyage voyage = voyageRepository.findByVoyageNumber(number).orElse(null);
            if (voyage == null) {
                // 便そのものが消えている。**空きの問題ではない**ため、ここでは扱わない
                continue;
            }
            if (!voyage.hasCapacityFor(required, assigned.get(number))) {
                full.add(number.value());
            }
        }
        return List.copyOf(full);
    }
}
