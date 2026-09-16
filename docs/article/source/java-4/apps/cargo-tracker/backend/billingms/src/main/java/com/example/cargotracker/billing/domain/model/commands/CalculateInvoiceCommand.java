package com.example.cargotracker.billing.domain.model.commands;

import com.example.cargotracker.billing.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.billing.domain.model.valueobjects.ShipperType;
import com.example.cargotracker.billing.domain.model.valueobjects.TransportRecord;
import java.math.BigDecimal;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 輸送料金を算出する（US21 §受入基準 1・3）。
 *
 * <p><b>集約の外で組み立てた入力を渡す。</b> 集約は投影を読めないので、輸送実績も
 * 荷主の契約も application 層が読み取りモデルから組み立てて渡す。</p>
 *
 * <p><b>割引は別のコマンドにしない</b>（計画の注 N10）。別にすると、割引の無い
 * 請求書が一瞬見える状態が正常系として存在する。</p>
 *
 * @param quotedAmount 見積時の概算。<b>見積を経ない予約では {@code null}</b>（注 N12）
 * @param invoiceId 請求書の識別子（送る側が採る）
 * @param bookingId 予約。<b>有効な請求書は予約ごとに 1 通</b>（不変条件 2）
 * @param shipperId 荷主
 * @param shipperName 荷主名。crypto-shredding 後は {@code null}（ADR-0003）
 * @param shipperType 荷主種別。割引が当たるかを決める
 * @param discountRate 契約割引率。無ければ 0%
 * @param contractNumber 契約番号。明細の割引行に出す（S61）
 * @param transport 輸送実績（区間・重量・貨物種別・両端）
 * @param calculatedBy 算出した人。連鎖からの算出では利用者名を持たない
 */
public record CalculateInvoiceCommand(
        @TargetEntityId String invoiceId,
        String bookingId,
        String shipperId,
        String shipperName,
        ShipperType shipperType,
        DiscountRate discountRate,
        String contractNumber,
        TransportRecord transport,
        // 見積時の概算（注 N12）。**見積を経ない予約では null。** そのまま持ち、
        // 計算し直さない（不変条件 7）——見積の時点の数字であることに意味がある。
        BigDecimal quotedAmount,
        String calculatedBy) {
}
