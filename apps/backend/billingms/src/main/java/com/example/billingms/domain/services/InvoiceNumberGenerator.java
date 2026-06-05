package com.example.billingms.domain.services;

import com.example.billingms.infrastructure.repositories.mybatis.InvoiceSummaryMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 請求書番号採番ジェネレータ（US23、IT7 T4.2、iteration_plan-7.md L111 / L214）。
 *
 * <p>形式: {@code INV-YYYYMMDD-XXXX}（YYYYMMDD: 採番日、XXXX: 当日 4 桁シーケンス）。
 * Read Model（{@code invoice} テーブル）の当日採番済 invoice_number 末尾 4 桁の最大値を取得して
 * +1 でインクリメントする。当日採番なしなら 0001。</p>
 *
 * <p>日次上限は 9999（当日 9999 件まで）。超過時は {@link IllegalStateException}。IT8 では
 * クラスタ対応として ON CONFLICT 再試行を追加する（現実装は billingms 単一 instance 前提）。</p>
 */
@Component
public class InvoiceNumberGenerator {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PREFIX = "INV";
    private static final int MAX_SEQUENCE = 9999;

    private final InvoiceSummaryMapper mapper;
    private final Clock clock;

    public InvoiceNumberGenerator(InvoiceSummaryMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * 当日の次の invoice_number を採番する。
     *
     * @return {@code INV-YYYYMMDD-XXXX} 形式の請求書番号
     * @throws IllegalStateException 当日採番が 9999 件を超えた場合
     */
    public String next() {
        LocalDate today = LocalDate.now(clock);
        String yyyymmdd = today.format(YYYYMMDD);
        Integer current = mapper.findMaxInvoiceNumberSequenceForDate(yyyymmdd);
        int nextSeq = (current == null ? 1 : current + 1);
        if (nextSeq > MAX_SEQUENCE) {
            throw new IllegalStateException(
                    "日次採番上限を超過しました（" + yyyymmdd + ": " + nextSeq + "）");
        }
        return String.format("%s-%s-%04d", PREFIX, yyyymmdd, nextSeq);
    }
}
