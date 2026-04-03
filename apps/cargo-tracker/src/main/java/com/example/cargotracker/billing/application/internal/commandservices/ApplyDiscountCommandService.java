package com.example.cargotracker.billing.application.internal.commandservices;

import com.example.cargotracker.billing.application.internal.outboundservices.ShipperDiscountQueryPort;
import com.example.cargotracker.billing.domain.model.aggregates.FreightCharge;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.domain.model.commands.ApplyDiscountCommand;
import com.example.cargotracker.billing.domain.model.repository.FreightChargeRepository;
import com.example.cargotracker.billing.domain.model.services.DiscountPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 法人割引を適用するアプリケーションサービス。
 *
 * <p>処理フロー：
 * <ol>
 *   <li>FreightCharge を ID で取得する</li>
 *   <li>予約 ID から荷主の割引率を ACL 経由で取得する</li>
 *   <li>{@link DiscountPolicy} で割引額を計算する</li>
 *   <li>{@link FreightCharge#applyAdjustment(BigDecimal)} で割引を適用する</li>
 *   <li>FreightCharge を保存する</li>
 * </ol>
 */
@Service
@Transactional
public class ApplyDiscountCommandService {

    private final FreightChargeRepository freightChargeRepository;
    private final ShipperDiscountQueryPort shipperDiscountQueryPort;
    private final DiscountPolicy discountPolicy;

    public ApplyDiscountCommandService(FreightChargeRepository freightChargeRepository,
                                       ShipperDiscountQueryPort shipperDiscountQueryPort,
                                       DiscountPolicy discountPolicy) {
        this.freightChargeRepository = freightChargeRepository;
        this.shipperDiscountQueryPort = shipperDiscountQueryPort;
        this.discountPolicy = discountPolicy;
    }

    /**
     * 指定された輸送料金に法人割引を適用する。
     *
     * @param command 割引適用コマンド
     * @throws IllegalArgumentException 輸送料金が見つからない場合
     * @throws IllegalStateException    輸送料金が CONFIRMED 状態の場合
     */
    public void applyDiscount(ApplyDiscountCommand command) {
        FreightId freightId = FreightId.of(command.freightChargeId());

        FreightCharge charge = freightChargeRepository.findById(freightId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "輸送料金が見つかりません: " + command.freightChargeId()));

        BigDecimal discountRate = shipperDiscountQueryPort
                .findDiscountRateByBookingId(command.bookingId());

        BigDecimal discountAmount = discountPolicy.calculateDiscount(
                charge.getBaseAmount(), discountRate);

        charge.applyAdjustment(discountAmount);
        freightChargeRepository.save(charge);
    }
}
