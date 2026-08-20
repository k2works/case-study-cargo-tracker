package com.example.bookingms.application.port;

import com.example.bookingms.domain.model.Cargo;

/**
 * 一覧に出す 1 件（貨物予約と、その荷主の名前）。
 *
 * <p>荷主名は集約の持ち物ではない（別集約の属性で、予約の不変条件に関わらない）。
 * しかし営業担当者は社名で予約を探すため、結果に社名が無いと同名の別会社が混ざって
 * いないかを画面で確かめられない。読むためだけの組み合わせとしてここで表す。
 *
 * @param cargo 貨物予約
 * @param shipperName 荷主の名前
 */
public record CargoSummary(Cargo cargo, String shipperName) {
}
