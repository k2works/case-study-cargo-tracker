package com.example.cargotracker.exception.application.internal.outboundservices;

/**
 * 追跡番号存在確認ポート（ACL）。
 * exception BC から tracking BC への依存を抽象化する。
 */
public interface TrackingExistencePort {
    /**
     * 追跡番号が存在することを確認する。
     * 存在しない場合は TrackingNotFoundException をスローする。
     */
    void verifyExists(String trackingNumber);
}
