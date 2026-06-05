package com.example.billingms.domain.services;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Invoice 集約識別子のジェネレータ（IT7 review M1 / architect 対応、ADR-0012 整合）。
 *
 * <p>{@code bookingId} から決定論的に invoiceId を派生（UUID v5 風）させる。これにより
 * event store リプレイや Kafka at-least-once 重複配信が起きても同一 invoiceId に集約され、
 * Axon の {@code AggregateIdentifierAlreadyExistsException} で早期に冪等吸収できる。</p>
 *
 * <p>従来の {@code UUID.randomUUID()} 方式は invoiceId がリプレイ毎に変わり、DB UNIQUE 違反 →
 * {@code CommandExecutionException} → WARN スキップで吸収していたが、Read Model 投影が
 * 走った後で UNIQUE 違反になるため無駄な処理コストが発生していた（ADR-0012 §3 規約と不整合）。</p>
 *
 * <p>UUID v5 は SHA-1 + namespace の標準仕様。本実装は Java 標準ライブラリのみで完結する
 * 実用版として、固定 namespace 文字列 + bookingId を SHA-1 ハッシュし、UUID として整形する。</p>
 */
@Component
public class InvoiceIdGenerator {

    /** Billing 用の固定 namespace（変更すると既存 invoiceId と整合しなくなるため不変）。 */
    private static final String NAMESPACE = "billing.invoice.v1";

    /**
     * {@code bookingId} から決定論的 invoiceId を派生する。
     *
     * @param bookingId 予約識別子（null / 空文字は不許可）
     * @return {@code UUID} 形式の invoiceId（同 bookingId からは必ず同一 UUID）
     * @throws IllegalArgumentException bookingId が null / 空文字の場合
     */
    public String fromBookingId(String bookingId) {
        if (bookingId == null || bookingId.isBlank()) {
            throw new IllegalArgumentException("bookingId は必須です");
        }
        byte[] hash = sha1((NAMESPACE + ":" + bookingId).getBytes(StandardCharsets.UTF_8));
        // SHA-1 ハッシュ 20 byte の先頭 16 byte を UUID にマップ（RFC 4122 v5 風）
        long msb = bytesToLong(hash, 0);
        long lsb = bytesToLong(hash, 8);
        // version 5 と variant 2 のビットをセット
        msb = (msb & 0xFFFFFFFFFFFF0FFFL) | 0x0000000000005000L;
        lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb).toString();
    }

    private static byte[] sha1(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 が利用できません", e);
        }
    }

    private static long bytesToLong(byte[] bytes, int offset) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result = (result << 8) | (bytes[offset + i] & 0xFFL);
        }
        return result;
    }
}
