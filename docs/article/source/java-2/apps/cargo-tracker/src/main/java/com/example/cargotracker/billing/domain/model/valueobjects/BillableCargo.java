package com.example.cargotracker.billing.domain.model.valueobjects;

/**
 * 請求できる貨物かの判定（US21 の受入基準 1）。
 *
 * <p><strong>「引取済」状態の予約に対して料金算出を開始できる。</strong>
 * ただし<strong>訂正・取り消しの申請中は開始できない</strong>（IT12 持ち越し C8）。
 * 取り消されるかもしれない引取をもとに請求書を出すと、
 * 出した後で引取が無かったことになる。
 * <strong>US36 が「精算済みには申請できない」と定めた裏返しであり、
 * 両側から塞がないと隙間が残る。</strong>
 *
 * <p><strong>二重請求は業務の言葉で拒む。</strong> DB の一意制約
 * （{@code invoice.booking_id}）でも防いでいるが、制約に頼ると画面には 500 が出る。
 *
 * @param claimed             引取が済んでいるか
 * @param correctionRequested 訂正・取り消しが申請中か
 * @param alreadyInvoiced     すでに請求書が作られているか
 * @param hasTransportRecord  経路（区間）の記録があるか。<strong>無ければ距離係数が 0 になり、
 *                            料金の算出そのものが成り立たない</strong>（レビュー H3）
 */
public record BillableCargo(
        boolean claimed, boolean correctionRequested, boolean alreadyInvoiced,
        boolean hasTransportRecord) {

    /** 請求できるか。**画面の出し分けは本述語をそのまま呼ぶ。** */
    public boolean isBillable() {
        return reasonNotBillable() == null;
    }

    /**
     * 請求できない理由。
     *
     * <p><strong>理由がある = 請求できない</strong>を一致させる。2 つの述語が
     * 別々に判定すると、「請求できないのに理由が空」という形が生まれる。
     *
     * @return 請求できるなら {@code null}
     */
    public String reasonNotBillable() {
        if (!claimed) {
            return "引取が完了していないため請求できません";
        }
        if (correctionRequested) {
            return "引取記録の訂正・取り消しが申請中のため請求できません";
        }
        if (alreadyInvoiced) {
            return "すでに請求書が作成されています";
        }
        if (!hasTransportRecord) {
            // **業務の言葉で拒む**（レビュー H3）。ここを抜けると距離係数 0 が
            // FreightChargeCalculator まで届き、画面には 500 が出る
            return "経路の記録が無いため請求できません";
        }
        return null;
    }
}
