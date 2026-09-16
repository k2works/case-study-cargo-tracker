package com.example.cargotracker.booking.application;

import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.QuotationTerms;
import com.example.cargotracker.booking.infrastructure.persistence.QuotationMapper;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 予約が見積とどう違うかを項目名で返す（US01・正典の不変条件 3）。
 *
 * <p><b>断らない。</b> 荷主の事情は見積のあとで変わる——重量が増えることも、
 * 期限が延びることもある。断ると業務が止まるので、<b>何がどう違うかを
 * 知らせる</b>にとどめる。</p>
 *
 * <p><b>「何から何へ」まで返す。</b> 項目名だけでは、営業担当者は見積を開き
 * 直して見比べることになる。</p>
 *
 * <p><b>集約ではなく投影を読む。</b> 差分は予約を受け付けたあとに出す表示で、
 * 業務の判断（受け付けるかどうか）には使わない——集約を復元するのは
 * 「判断に使う状態」が要るときだけである。</p>
 */
@Component
public class QuotationDiff {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(QuotationDiff.class);

    private final QuotationMapper quotations;

    public QuotationDiff(QuotationMapper quotations) {
        this.quotations = quotations;
    }

    /**
     * 違いの説明。
     *
     * <p><b>見積番号が無い・見つからない・読めないときは空</b>を返す。
     * 違いを知らせられないだけで、<b>予約そのものは通す</b>——番号の打ち間違いで
     * 業務が止まるほうが重い。</p>
     */
    public List<String> differences(String quotationId, BookCargoCommand command) {
        QuotationMapper.QuotationRow quoted = quoted(quotationId);
        if (quoted == null) {
            return List.of();
        }
        // **比較は 1 か所**（QuotationTerms）。集約の diffAgainst と同じものを使う。
        return new QuotationTerms(quoted.originUnLocode(), quoted.destinationUnLocode(),
                quoted.arrivalDeadline(), quoted.cargoType(), quoted.weightKg())
                .differencesAgainst(QuotationTerms.of(command));
    }

    /**
     * 見積の行。**読めなければ null**（予約は通す）。
     *
     * <p>差分と概算は<b>同じ 1 行から取る</b>。別々に読むと、片方だけが
     * 古い見積を見ることになる。</p>
     */
    public QuotationMapper.QuotationRow quoted(String quotationId) {
        if (quotationId == null || quotationId.isBlank()) {
            // 見積を経ない予約。**そのほうが多い**ので、無いことを異常にしない。
            return null;
        }
        try {
            // 打ち間違いか、投影がまだなら null。**予約は通す**——番号の
            // 打ち間違いで業務が止まるほうが重い。
            return quotations.find(quotationId);
        } catch (RuntimeException e) {
            // **読めなくても予約は通す。** 違いを知らせられないだけで、
            // 予約そのものは既に受け付けてある。
            log.warn("見積 {} と突き合わせられませんでした: {}", quotationId, e.toString());
            return null;
        }
    }
}
