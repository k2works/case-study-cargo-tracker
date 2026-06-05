package com.example.billingms.domain.services;

/**
 * 請求書番号シーケンス取得ポート（US23、IT7 review M2 対応 / DIP 回復）。
 *
 * <p>{@link InvoiceNumberGenerator} は本ポートに依存し、具象 Mapper には依存しない。
 * infrastructure 層で {@code MybatisInvoiceNumberSequenceRepository} が Mapper を呼んで
 * 実装する。テストではインメモリ実装（{@code FakeInvoiceNumberSequenceRepository}）を
 * 注入することで 9999 上限ロジック等を Mapper モックなしで検証できる。</p>
 */
public interface InvoiceNumberSequenceRepository {

    /**
     * 指定日付の当日採番済 invoice_number 末尾 4 桁の最大値を取得する。
     *
     * @param yyyymmdd 採番対象日（YYYYMMDD 8 桁文字列）
     * @return 最大シーケンス番号（1〜9999）または {@code null}（当日採番なし）
     */
    Integer findMaxSequenceForDate(String yyyymmdd);
}
