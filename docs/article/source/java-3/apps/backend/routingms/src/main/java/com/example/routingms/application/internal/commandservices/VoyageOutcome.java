package com.example.routingms.application.internal.commandservices;

import com.example.routingms.domain.model.aggregates.Voyage;
import com.example.routingms.domain.model.valueobjects.VoyageDifference;

/**
 * 登録・更新の結果。
 *
 * <p>真偽値や例外で返すと、呼び出し側が「なぜそうなったか」を組み立て直すことになる。
 * ここで場合を名前で分け、画面はそれぞれに対応する。
 */
public sealed interface VoyageOutcome {

    /** 登録・更新できた。 */
    record Registered(Voyage voyage) implements VoyageOutcome {
    }

    /**
     * 同じ航海番号が既にある。失敗ではなく、利用者への問いかけである。
     *
     * <p>差分を添えて「上書きするか」を選ばせる。差分が無ければ「変更ありません」と伝える。
     */
    record AlreadyExists(Voyage existing, VoyageDifference difference) implements VoyageOutcome {
    }

    /** 上書きしようとした航海が無い。番号の打ち間違いを新規登録にしないための場合分け。 */
    record NotFound(String voyageNumber) implements VoyageOutcome {
    }
}
