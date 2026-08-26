package com.example.billingms.application.port;

import com.example.billingms.domain.model.InvoiceId;

/**
 * 請求番号の採番（出力ポート・[ADR-011] と同じ形）。
 *
 * <p><strong>DB のシーケンスに任せる。</strong>MAX+1 の自前採番は、同時に 2 件発行された
 * ときに衝突する。
 */
public interface InvoiceNumbering {

    InvoiceId next();
}
