package com.example.cargotracker.billing.application.internal.commandservices;

import com.example.cargotracker.billing.application.internal.outboundservices.FreightBookingQueryPort;
import com.example.cargotracker.billing.application.internal.outboundservices.FreightBookingQueryPort.FreightBookingSummary;
import com.example.cargotracker.billing.domain.model.aggregates.FreightCharge;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.domain.model.commands.CalculateFreightCommand;
import com.example.cargotracker.billing.domain.model.repository.FreightChargeRepository;
import com.example.cargotracker.billing.domain.model.services.FreightCalculationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 輸送料金算出コマンドサービス。
 */
@Service
@Transactional
public class CalculateFreightCommandService {

    private final FreightChargeRepository freightChargeRepository;
    private final FreightBookingQueryPort freightBookingQueryPort;
    private final FreightCalculationService freightCalculationService;

    public CalculateFreightCommandService(FreightChargeRepository freightChargeRepository,
                                          FreightBookingQueryPort freightBookingQueryPort,
                                          FreightCalculationService freightCalculationService) {
        this.freightChargeRepository = freightChargeRepository;
        this.freightBookingQueryPort = freightBookingQueryPort;
        this.freightCalculationService = freightCalculationService;
    }

    /**
     * 確定済み予約から輸送料金を算出する。
     *
     * @param command 算出コマンド（予約 ID を含む）
     * @return 生成された輸送料金の ID
     * @throws BookingNotFoundException 確定済み予約が見つからない場合
     */
    public FreightId calculate(CalculateFreightCommand command) {
        FreightBookingSummary summary = freightBookingQueryPort
                .findCalculableBookingById(command.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(command.bookingId()));

        BigDecimal baseAmount = freightCalculationService.calculateBaseAmount(
                summary.weightKg(), summary.cargoType());

        FreightCharge charge = FreightCharge.calculate(
                FreightId.generate(), summary.bookingId(), baseAmount);
        if (command.adjustmentAmount() != null) {
            charge.applyAdjustment(command.adjustmentAmount());
        }

        freightChargeRepository.save(charge);

        return charge.getId();
    }

    /**
     * DRAFT 状態の輸送料金を確定する。
     *
     * @param id 輸送料金 ID
     * @throws IllegalArgumentException 輸送料金が見つからない場合
     */
    public void confirm(FreightId id) {
        FreightCharge charge = freightChargeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "輸送料金が見つかりません: " + id.value()));

        charge.confirm();
        freightChargeRepository.save(charge);
    }
}
