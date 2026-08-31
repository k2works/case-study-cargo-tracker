package com.example.bookingms.domain.repository;

import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.aggregates.Shipper;

/**
 * 一覧に出す 1 件（貨物予約と、その荷主の名前）。
 *
 * <p>荷主名は集約の持ち物ではない（別集約の属性で、予約の不変条件に関わらない）。
 * しかし営業担当者は社名で予約を探すため、結果に社名が無いと同名の別会社が混ざって
 * いないかを画面で確かめられない。読むためだけの組み合わせとしてここで表す。
 *
 * <p>荷主コードも同じ理由でここに置く。<strong>由来（シミュレーションか）は
 * コードの帯で決まる</strong>（[ADR-030] 決定 3）ため、荷主の集約を組み立てずに
 * 判定できる必要がある——一覧の絞り込みは 1 件ずつ集約を復元する場所ではない。
 *
 * @param cargo 貨物予約
 * @param shipperName 荷主の名前
 * @param shipperCode 荷主コード（由来の判定に使う）
 */
public record CargoSummary(Cargo cargo, String shipperName, String shipperCode) {

    /** シミュレーション由来か（[ADR-030] 決定 3）。判定は {@link Shipper} に 1 つ置く。 */
    public boolean simulated() {
        return Shipper.isSimulatedCode(shipperCode);
    }
}
