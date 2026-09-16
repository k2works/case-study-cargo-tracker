package com.example.cargotracker.billing.domain.model.valueobjects;

/**
 * 港の地域区分（正典の料金計算「区間係数」）。
 *
 * <p>区間の係数は<b>両端の重いほう</b>で決まる。国内どうしなら国内、片方が
 * 遠洋なら遠洋である。</p>
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

    /** 画面と明細に出す呼び名。<b>列挙名を出さない</b>（読む人は業務の言葉で読む）。 */
    public String label() {
        return label;
    }

    /** 重いほう（係数の大きいほう）。宣言順が重さの順である。 */
    public PortRegion heavier(PortRegion other) {
        return ordinal() >= other.ordinal() ? this : other;
    }
}
