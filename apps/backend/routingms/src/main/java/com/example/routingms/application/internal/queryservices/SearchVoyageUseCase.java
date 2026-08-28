package com.example.routingms.application.internal.queryservices;

import com.example.routingms.application.port.VoyageRepository;
import com.example.routingms.application.port.VoyageSearchCriteria;
import com.example.routingms.domain.model.Voyage;
import com.example.routingms.domain.model.VoyageNumber;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 航海スケジュールの検索（US07）。
 *
 * <p>件数の上限を必ず置く。置かないと、航海が増えたときに一覧の読み込みだけが重くなり、
 * 原因が「増えたこと」だと気づけない。そして<strong>切ったことを黙らない</strong>。
 * 黙って切ると、経路設計者は「条件に合う航海はこれで全部だ」と読む。
 */
@Service
public class SearchVoyageUseCase {

    /** 一覧の上限。画面で目を通せる範囲に合わせる。 */
    public static final int DEFAULT_LIMIT = 50;

    private final VoyageRepository voyages;

    public SearchVoyageUseCase(VoyageRepository voyages) {
        this.voyages = voyages;
    }

    /**
     * @param voyages 上限までの航海
     * @param totalCount 条件に合う総数（上限で切る前）
     * @param limit 適用した上限
     */
    public record Result(List<Voyage> voyages, int totalCount, int limit) {

        /** 上限で切ったか。切ったなら画面は条件を絞るよう促す。 */
        public boolean truncated() {
            return totalCount > voyages.size();
        }
    }

    /**
     * 航海番号で 1 件取り出す（US25）。
     *
     * <p>更新のたびに全区間を打ち直させないために要る。打ち直しは、その過程で別の項目が
     * 変わる事故を招く。
     */
    public Optional<Voyage> findByNumber(VoyageNumber voyageNumber) {
        return voyages.findByVoyageNumber(voyageNumber);
    }

    public Result search(VoyageSearchCriteria criteria) {
        return new Result(
                voyages.search(criteria, DEFAULT_LIMIT),
                voyages.countMatching(criteria),
                DEFAULT_LIMIT);
    }
}
