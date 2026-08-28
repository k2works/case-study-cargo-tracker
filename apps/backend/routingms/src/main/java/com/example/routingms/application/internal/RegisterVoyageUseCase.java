package com.example.routingms.application.internal;

import com.example.routingms.application.port.VoyageRepository;
import com.example.routingms.domain.model.Voyage;
import com.example.routingms.domain.model.VoyageDifference;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 航海スケジュールの登録と更新（US24・US25）。
 *
 * <p>同じ航海番号での登録を拒否しない。経路設計者が同じ番号を入れるのは多くの場合
 * スケジュールの差し替えであり、そこで止めると別の番号を作る（同じ航海が 2 つになる）か、
 * 一覧から探し直すことになる。差分を見せて上書きを選ばせる。
 */
@Service
public class RegisterVoyageUseCase {

    private final VoyageRepository voyages;
    private final ZoneId businessZone;

    public RegisterVoyageUseCase(VoyageRepository voyages, ZoneId businessZone) {
        this.voyages = voyages;
        this.businessZone = businessZone;
    }

    /** 登録する。同じ航海番号が既にあれば差分を返し、上書きは呼び出し側の選択に委ねる。 */
    public VoyageOutcome register(RegisterVoyageCommand command) {
        Voyage incoming = toVoyage(command);
        Optional<Voyage> existing = voyages.findByVoyageNumber(command.voyageNumber());
        if (existing.isPresent()) {
            return new VoyageOutcome.AlreadyExists(existing.get(),
                    VoyageDifference.between(existing.get(), incoming, businessZone));
        }
        return new VoyageOutcome.Registered(voyages.save(incoming));
    }

    /**
     * 差分を確認したうえで上書きする。
     *
     * <p>存在しない航海番号は登録に倒さない。上書きの画面は「既にある航海を差し替える」
     * 文脈であり、そこから新しい航海が生まれると、番号の打ち間違いが新規登録になる。
     */
    public VoyageOutcome overwrite(RegisterVoyageCommand command) {
        if (voyages.findByVoyageNumber(command.voyageNumber()).isEmpty()) {
            return new VoyageOutcome.NotFound(command.voyageNumber().value());
        }
        return new VoyageOutcome.Registered(voyages.save(toVoyage(command)));
    }

    private Voyage toVoyage(RegisterVoyageCommand command) {
        return Voyage.register(command.voyageNumber(), command.vesselName(), command.carrierName(),
                command.supportedCargoTypes(), command.schedule());
    }
}
