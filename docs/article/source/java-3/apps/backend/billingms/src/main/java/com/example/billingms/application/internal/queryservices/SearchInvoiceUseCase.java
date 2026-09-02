package com.example.billingms.application.internal.queryservices;

import com.example.billingms.domain.model.valueobjects.InvoiceSearchCriteria;
import com.example.billingms.domain.repository.InvoiceRepository;
import org.springframework.stereotype.Service;

/**
 * 請求書を探し、その条件の件数と合計を返す（US38）。
 *
 * <p>月末の締めでは「その月に出した請求書の合計」と「特定の荷主の請求書」を
 * 繰り返し引く。<strong>締めの作業を表計算から引き上げる</strong>のがこの機能の目的である。
 */
@Service
public class SearchInvoiceUseCase {

    /**
     * 一度に返す件数の上限。
     *
     * <p><strong>切ったことは件数で伝える。</strong>黙って切ると、担当者は
     * 「一覧に出ていないから無い」と読む（予約一覧・通関一覧と同じ形）。
     */
    public static final int SEARCH_LIMIT = 200;

    private final InvoiceRepository invoices;

    public SearchInvoiceUseCase(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    /**
     * 条件に合う請求書・件数・合計を返す。
     *
     * <p><strong>3 つとも同じ条件で引く。</strong>別々に組み立てると、片方だけ直した
     * ときに「12 件あります」と出るのに開くと 3 件、という形になる。
     */
    public InvoiceSearchResult search(InvoiceSearchCriteria criteria) {
        return new InvoiceSearchResult(invoices.search(criteria, SEARCH_LIMIT),
                invoices.count(criteria), invoices.total(criteria));
    }
}
