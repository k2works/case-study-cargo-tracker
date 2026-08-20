package com.example.routingms.application.internal;

import com.example.routingms.application.port.VoyageRepository;
import com.example.routingms.application.port.VoyageSearchCriteria;
import com.example.routingms.domain.model.Voyage;
import java.util.List;

/**
 * 航海スケジュールの検索（US07）。
 *
 * <p>件数の上限を必ず置く。置かないと、航海が増えたときに一覧の読み込みだけが重くなり、
 * 原因が「増えたこと」だと気づけない。そして<strong>切ったことを黙らない</strong>。
 * 黙って切ると、経路設計者は「条件に合う航海はこれで全部だ」と読む。
 */
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

    public Result search(VoyageSearchCriteria criteria) {
        return new Result(
                voyages.search(criteria, DEFAULT_LIMIT),
                voyages.countMatching(criteria),
                DEFAULT_LIMIT);
    }
}
