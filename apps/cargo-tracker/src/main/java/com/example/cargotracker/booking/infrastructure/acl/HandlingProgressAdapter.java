package com.example.cargotracker.booking.infrastructure.acl;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.handling.application.internal.outboundservices.acl.HandlingProgressPort;
import java.util.ConcurrentModificationException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link HandlingProgressPort} の実装（ACL のアダプタ）。
 *
 * <p><strong>判断はドメインが行う。</strong> ここでするのは、荷役が伝えてきた事実を
 * 集約に渡し、結果を保存することだけである。
 */
@Component
public class HandlingProgressAdapter implements HandlingProgressPort {

    private final CargoRepository cargoRepository;

    public HandlingProgressAdapter(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    @Override
    @Transactional
    public void markMisrouted(UUID bookingId) {
        cargoRepository.findById(new BookingId(bookingId)).ifPresent(cargo -> {
            cargo.markMisrouted();
            // **衝突の合図を捨てない。** 捨てると、荷役だけが記録されて誤配が
            // 黙って落ちる。誤配が経路状態に残らないことは、現場から見て
            // 最も気づけない壊れ方である（IT6 レビュー H2）
            if (!cargoRepository.updateRouting(cargo)) {
                throw new ConcurrentModificationException(
                        "他の操作が先に行われました。最新の内容を確認してください");
            }
        });
    }

    /**
     * 最初の積込であれば輸送を開始する（遷移表 #6）。
     *
     * <p><strong>述語で確かめてから進める。</strong> すでに輸送中の貨物に積込を
     * 記録することは正しい業務（積み替え）であり、例外にしてはならない。
     */
    @Override
    @Transactional
    public void startTransportIfNotStarted(UUID bookingId) {
        cargoRepository.findById(new BookingId(bookingId)).ifPresent(cargo -> {
            if (!cargo.canStartTransport()) {
                return;
            }
            cargo.startTransport();
            if (!cargoRepository.update(cargo)) {
                throw new ConcurrentModificationException(
                        "他の操作が先に行われました。最新の内容を確認してください");
            }
        });
    }
}
