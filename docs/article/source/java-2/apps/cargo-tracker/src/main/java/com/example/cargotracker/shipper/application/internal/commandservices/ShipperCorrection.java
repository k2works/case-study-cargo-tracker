package com.example.cargotracker.shipper.application.internal.commandservices;

import com.example.cargotracker.shipper.domain.model.valueobjects.Address;
import com.example.cargotracker.shipper.domain.model.valueobjects.CorporateContract;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;

/**
 * 訂正で差し替える値のひと組（US32 + US03）。
 *
 * <p><strong>荷主コードと荷主種別を持たない。</strong> US32 が「変更できない」と定めており、
 * <strong>受け取らないことがその実装である</strong>（{@code ShipperEditForm} と同じ理由）。
 *
 * <p>まとめた理由は、契約を訂正できるようにした結果 Checkstyle の引数上限（7）を
 * 超えたためである。<strong>上限に当たったら、引数を隠す箱ではなく「一度に差し替える
 * ひと組」として意味のある単位にする。</strong>
 *
 * @param contract 法人契約。<strong>{@code null} なら契約を変えない</strong>。
 *                 個人荷主に適用されないことはアプリケーション層が現在の荷主を見て守る
 */
public record ShipperCorrection(
        ShipperName name,
        Email email,
        Phone phone,
        Address address,
        CorporateContract contract) {

    public ShipperCorrection {
        if (name == null || email == null || phone == null || address == null) {
            throw new IllegalArgumentException("荷主名・メール・電話・住所は必須です");
        }
    }
}
