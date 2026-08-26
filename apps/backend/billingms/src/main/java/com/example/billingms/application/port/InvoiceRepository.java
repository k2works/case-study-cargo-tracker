package com.example.billingms.application.port;

import com.example.billingms.domain.model.Invoice;
import java.util.List;
import java.util.Optional;

/** 精算書の永続化（出力ポート）。 */
public interface InvoiceRepository {

    void save(Invoice invoice);

    Optional<Invoice> findById(String invoiceId);

    List<Invoice> findAll();

    /**
     * その予約に精算書が発行済みか（決定 4）。
     *
     * <p><strong>二重請求を防ぐ。</strong>DB の UNIQUE 制約でも守るが、制約だけだと
     * 画面に 500 が出る——利用者には「なぜ断られたか」が伝わらない。
     */
    boolean existsForBooking(String bookingId);
}
