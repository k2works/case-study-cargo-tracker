package com.example.cargotracker.booking.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 荷主登録の入出力（UI 設計 S11）。 */
public final class ShipperDtos {

    private ShipperDtos() {
    }

    public record RegisterShipperRequest(
            @NotBlank String name,
            @NotNull String shipperType,
            @NotBlank String email,
            String phone,
            String address,
            String contractNumber,
            BigDecimal discountRate,
            // 重複の問いかけに「続ける」と答えたか。
            // 1 段目（登録前の存在確認）は「たぶん重複している」という助言であって
            // 断定ではない。営業が同名・同メールで登録したい事情もあるので、断らずに
            // 問いかけ、続けるなら通す。最後の砦は投影の UNIQUE と要確認一覧
            // （domain-model.md の一意性の三段）。
            // 省略可能。primitive にすると、送っていないクライアントの要求が
            // 丸ごと 400 になる（実際に既存の呼び出しが全部落ちた）。
            Boolean acknowledgedDuplicate) {

        /** 省略は「まだ問いかけていない」＝続行の意思なし。 */
        public boolean duplicateAcknowledged() {
            return Boolean.TRUE.equals(acknowledgedDuplicate);
        }
    }

    public record RegisterShipperResponse(String shipperId) {
    }

    /** 受け付けたが投影がまだのときに返す（202）。画面は「反映中」を出す。 */
    public record PendingResponse(String shipperId, String message) {
    }
}
