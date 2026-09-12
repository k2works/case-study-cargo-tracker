package com.example.cargotracker.booking.domain.model.valueobjects;

/**
 * 港の地域区分（正典の料金計算「区間係数」）。
 *
 * <p>区間の係数は<b>両端の重いほう</b>で決まる。国内どうしなら国内、片方が
 * 遠洋なら遠洋である。</p>
 *
 * <p><b>billingms の同名の列挙型とは別の型である。</b> 共有カーネルに列挙型は
 * 置かない（{@code domain-model.md}）。同じ値であることは設定ファイルの出典が
 * 1 つであることで保たれ、同じ料率を読んでいることは
 * {@code RateTableParityTest} が固定する。</p>
 */
public enum PortRegion {

    /** 国内（1.0）。 */
    DOMESTIC("国内"),

    /** 近海（2.5）。 */
    NEAR_SEA("近海"),

    /** 遠洋（6.0）。 */
    OCEAN("遠洋");

    private final String label;

    PortRegion(String label) {
        this.label = label;
    }

    /** 画面に出す呼び名。<b>列挙名を出さない</b>（読む人は業務の言葉で読む）。 */
    public String label() {
        return label;
    }

    /** 重いほう（係数の大きいほう）。宣言順が重さの順である。 */
    public PortRegion heavier(PortRegion other) {
        return ordinal() >= other.ordinal() ? this : other;
    }
}
