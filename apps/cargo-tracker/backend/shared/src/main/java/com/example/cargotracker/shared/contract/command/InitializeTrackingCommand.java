package com.example.cargotracker.shared.contract.command;

import java.time.Instant;
import java.util.List;

/**
 * 追跡を開始する（US14）。bookingms → trackingms。<b>契約コマンドの 1 本目</b>
 * （architecture_backend.md「サービス越しのコマンド」）。
 *
 * <p><b>イベントではなくコマンドで送る。</b> これまでのサービス間はイベントの一方向
 * だった。追跡の開始は「起きたこと」ではなく「してほしいこと」で、失敗したら誰かが
 * 気づかなければならない（{@code process_state} に記録し、上限超過で補償する）。</p>
 *
 * <p><b>中身は文字列・数値・日付だけにする。</b> 識別子型（{@code TrackingNumber}）も
 * 列挙型（{@code TransportStatus}・{@code CargoType}）も共有カーネルに置かない決まりで、
 * 両 BC がそれぞれの型へ組み直す。同じ名前でも BC ごとに値と意味が違う。</p>
 *
 * <p><b>{@code legs} を落とさない。</b> 荷役（IT9）が {@code CargoSnapshot} の材料に
 * する。契約は追記専用で、あとから形を変えられない。「いま要らないから」で落とすと、
 * 購読側を作る IT で契約を変えることになる。</p>
 *
 * <p><b>状態を載せない。</b> 追跡を始めた直後がどの状態かは trackingms が決める
 * （{@code TransportStatus.NOT_RECEIVED}）。載せると、送る側が相手の状態機械を
 * 知っていることになる。</p>
 *
 * @param trackingNumber 発行済みの追跡番号（bookingms の投影が採番した）
 * @param bookingId 元の予約
 * @param originUnLocode 出発地の UN/LOCODE
 * @param destinationUnLocode 目的地の UN/LOCODE
 * @param cargoType 貨物種別の名前（{@code GENERAL} / {@code HAZARDOUS} / {@code REEFER}）
 * @param legs 確定した旅程。積む順。空にはならない（経路が決まってから発行する）
 * @param issuedAt 追跡番号を発行した時刻
 */
public record InitializeTrackingCommand(
        String trackingNumber,
        String bookingId,
        String originUnLocode,
        String destinationUnLocode,
        String cargoType,
        List<LegDto> legs,
        Instant issuedAt) {

    public InitializeTrackingCommand {
        legs = legs == null ? List.of() : List.copyOf(legs);
    }

    /** 旅程の 1 区間。順序が業務の意味を持つ（積む順）。 */
    public record LegDto(
            String voyageNumber,
            String loadUnLocode,
            String unloadUnLocode,
            Instant loadTime,
            Instant unloadTime) {
    }
}
