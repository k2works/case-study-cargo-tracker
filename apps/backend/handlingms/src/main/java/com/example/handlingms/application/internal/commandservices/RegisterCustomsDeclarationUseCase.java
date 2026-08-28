package com.example.handlingms.application.internal.commandservices;

import org.springframework.stereotype.Service;

import com.example.handlingms.application.internal.outboundservices.acl.CargoSnapshotFinder;
import com.example.handlingms.domain.model.aggregates.CustomsDeclaration;
import com.example.handlingms.domain.model.commands.RegisterCustomsDeclarationCommand;
import com.example.handlingms.domain.model.valueobjects.CargoBookingId;
import com.example.handlingms.domain.model.valueobjects.CargoSnapshot;
import com.example.handlingms.domain.model.valueobjects.CustomsStatus;
import com.example.handlingms.domain.model.valueobjects.DeclarationNumber;
import com.example.handlingms.domain.model.valueobjects.HandlingTrackingNumber;
import com.example.handlingms.domain.repository.CustomsDeclarationRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通関申告を登録する（US29-1・[ADR-025] 決定 7）。
 *
 * <p>ここが守るのは<strong>未決着の申告が 2 件にならない</strong>ことである。
 * 2 件あると、引取のガードがどちらの申告を見ればよいか決まらない。
 *
 * <p><strong>「最新の 1 件」を暗黙に選ぶ実装にしない。</strong>未決着が高々 1 件で
 * あることを登録側で守れば、ガードの「最新」は一意になる。
 */
@Service
public class RegisterCustomsDeclarationUseCase {

    private final CustomsDeclarationRepository declarations;
    private final CargoSnapshotFinder cargoes;

    public RegisterCustomsDeclarationUseCase(CustomsDeclarationRepository declarations,
            CargoSnapshotFinder cargoes) {
        this.declarations = declarations;
        this.cargoes = cargoes;
    }

    /**
     * 申告する。
     *
     * @throws IllegalArgumentException 追跡番号の貨物が見つからないとき
     * @throws IllegalStateException 決着していない申告があるとき、または通関済のとき
     */
    @Transactional
    public CustomsDeclaration register(RegisterCustomsDeclarationCommand command) {
        HandlingTrackingNumber trackingNumber =
                HandlingTrackingNumber.of(command.trackingNumber());
        CargoSnapshot cargo = cargoes.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "指定された追跡番号の貨物が見つかりません。番号を確かめてください"));
        CargoBookingId cargoBookingId = CargoBookingId.of(cargo.bookingId());

        // **決着していない申告があるあいだは 2 通目を受け付けない**（決定 7）
        declarations.findUnsettledByTrackingNumber(trackingNumber).ifPresent(unsettled -> {
            throw new IllegalStateException(
                    "この貨物には決着していない通関申告があります（現在: %s）。"
                            .formatted(unsettled.status().label())
                            + "先にその申告を処理してください");
        });
        // **通関済のあとに出し直さない。**引き取れる状態を、あとからの申告で覆さない
        declarations.findLatestByBookingId(cargoBookingId)
                .filter(latest -> latest.status() == CustomsStatus.CLEARED)
                .ifPresent(latest -> {
                    throw new IllegalStateException(
                            "この貨物はすでに通関済です。申告を出し直すことはできません");
                });

        return declarations.save(CustomsDeclaration.declare(
                DeclarationNumber.of(command.declarationNumber()), cargoBookingId, trackingNumber,
                command.declaredAt(), command.remarks(), command.declaredBy()));
    }
}
