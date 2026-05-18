package com.example.cargotracker.handlingms.application.eventhandlers;

import com.example.cargotracker.handlingms.domain.model.events.HandlingActivityRegisteredEvent;
import com.example.cargotracker.handlingms.domain.model.events.UnexpectedHandlingDetectedEvent;
import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.handlingms.infrastructure.persistence.CargoSnapshotMapper;
import com.example.cargotracker.handlingms.infrastructure.persistence.ClaimVerificationMapper;
import com.example.cargotracker.handlingms.infrastructure.persistence.ClaimVerificationRecord;
import com.example.cargotracker.handlingms.infrastructure.persistence.HandlingActivityMapper;
import com.example.cargotracker.handlingms.infrastructure.persistence.HandlingActivityRecord;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * HandlingActivityRegisteredEvent / UnexpectedHandlingDetectedEvent を受信し、
 * handling_activity Read Model を更新する EventHandler。
 *
 * <p>US15 受入条件 5: 記録後、荷主に状態変更通知が送信される
 * （IT5 はログのみ、実送信は IT6+）。</p>
 *
 * <p>Profile 除外規約は bookingms の {@code CargoProjectionsEventHandler} と同じ。</p>
 */
@Component
@Profile("!springboot-integration-test")
public class HandlingProjectionsEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HandlingProjectionsEventHandler.class);

    /** 引取完了時の貨物状態（US16）。bookingms の {@code BookingStatus.DELIVERED} と一致させる。 */
    private static final String STATUS_DELIVERED = "DELIVERED";

    private final HandlingActivityMapper handlingActivityMapper;
    private final ClaimVerificationMapper claimVerificationMapper;
    private final CargoSnapshotMapper cargoSnapshotMapper;

    public HandlingProjectionsEventHandler(
            HandlingActivityMapper handlingActivityMapper,
            ClaimVerificationMapper claimVerificationMapper,
            CargoSnapshotMapper cargoSnapshotMapper) {
        this.handlingActivityMapper = handlingActivityMapper;
        this.claimVerificationMapper = claimVerificationMapper;
        this.cargoSnapshotMapper = cargoSnapshotMapper;
    }

    @EventHandler
    @Transactional
    public void on(HandlingActivityRegisteredEvent event) {
        var record = new HandlingActivityRecord();
        record.setActivityId(event.activityId());
        // CargoSnapshot ACL の射影
        record.setBookingId(event.cargoSnapshot().bookingId());
        record.setTrackingNumber(event.trackingNumber().value());
        record.setOriginUnlocode(event.cargoSnapshot().origin().unLocode().value());
        record.setDestinationUnlocode(event.cargoSnapshot().destination().unLocode().value());
        record.setCargoType(event.cargoSnapshot().cargoType());
        // 荷役作業本体
        record.setHandlingType(event.handlingType().name());
        record.setOccurredAt(event.occurredAt());
        record.setUnlocode(event.location().unLocode().value());
        if (event.voyageNumber() != null) {
            record.setVoyageNumber(event.voyageNumber().value());
        }
        record.setHandlerId(event.operatorId().value());
        record.setUnexpected(event.unexpected());

        handlingActivityMapper.insert(record);

        // US16 受入3/4: CLAIM 種別は claim_verification 保存 + 貨物状態を DELIVERED に遷移
        if (event.handlingType() == HandlingType.CLAIM && event.claimVerification() != null) {
            var claim = event.claimVerification();
            var claimRecord = new ClaimVerificationRecord();
            claimRecord.setActivityId(event.activityId());
            claimRecord.setConsigneeName(claim.consigneeName());
            claimRecord.setSignatureRef(claim.signatureRef());
            claimRecord.setConfirmationCode(claim.confirmationCode());
            claimRecord.setVerifiedAt(claim.verifiedAt());
            claimVerificationMapper.insert(claimRecord);

            cargoSnapshotMapper.updateBookingStatusByTrackingNumber(
                    event.trackingNumber().value(), STATUS_DELIVERED);
            LOG.info("[CLAIM] 引取完了で貨物状態を DELIVERED に遷移: tracking={}",
                    event.trackingNumber().value());
        }

        // US15 受入5: 状態変更通知（IT5 はログのみ、実送信は IT6+）
        LOG.info("[NOTIFICATION] 荷主向け状態変更通知: tracking={} type={} location={}",
                event.trackingNumber().value(),
                event.handlingType(),
                event.location().unLocode().value());
    }

    @EventHandler
    public void on(UnexpectedHandlingDetectedEvent event) {
        // 警告ログ（US15 受入7）。trackingms 新設後は例外履歴テーブルに格上げ予定。
        LOG.warn("[UNEXPECTED HANDLING] tracking={} type={} actual={} expected_origin={} expected_destination={}",
                event.trackingNumber().value(),
                event.handlingType(),
                event.actualLocation().unLocode().value(),
                event.expectedOrigin().unLocode().value(),
                event.expectedDestination().unLocode().value());
    }
}
