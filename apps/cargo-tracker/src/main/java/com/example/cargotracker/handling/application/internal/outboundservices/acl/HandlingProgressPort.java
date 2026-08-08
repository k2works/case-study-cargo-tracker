package com.example.cargotracker.handling.application.internal.outboundservices.acl;

import java.util.UUID;

/**
 * 荷役の結果を予約に反映する出力ポート（Handling → Booking の ACL）。
 *
 * <p><strong>渡すのは予約 ID だけである。</strong> 荷役は Booking の状態遷移も
 * 経路状態も知らない。「何が起きたか」を伝え、どう反映するかは Booking が決める。
 *
 * <p>実装は Booking 側の {@code infrastructure/acl} が持つ。
 */
public interface HandlingProgressPort {

    /**
     * 誤配として記録する（荷役ビジネスルール 1）。
     *
     * <p>積込・荷降しが予定ルートから外れた場合にのみ呼ぶ。受領・引取の食い違いは
     * 警告に留まり、経路そのものは正しい。
     */
    void markMisrouted(UUID bookingId);

    /**
     * 最初の積込であれば輸送を開始する（遷移表 #6）。
     *
     * <p><strong>すでに輸送中なら何もしない。</strong> 積込は輸送中にも起きる
     * （積み替え）ため、そのたびに遷移を試みると正しい荷役の記録が拒否される。
     */
    void startTransportIfNotStarted(UUID bookingId);
}
