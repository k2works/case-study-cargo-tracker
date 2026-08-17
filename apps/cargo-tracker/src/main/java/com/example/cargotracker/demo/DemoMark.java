package com.example.cargotracker.demo;

/**
 * 自動実行が作ったデータの<strong>印</strong>。
 *
 * <p><strong>印が無いデータは消せない。</strong> 自動実行は動かすたびに荷主と貨物を作る。
 * 実際の登録と見分けが付かないと、片付けようとしたときに<strong>消してよいものと
 * いけないものを画面から判断できない</strong>。
 *
 * <p><strong>印は荷主に付ける。</strong> 貨物・追跡・荷役・請求はすべて荷主に紐づく
 * 派生データであり、<strong>荷主から辿れば取りこぼしが出ない</strong>。品名にも印を書くが、
 * こちらは一覧を目で見たときに分かるようにするためであり、削除の起点ではない。
 *
 * <p><strong>{@code DemoInstallMarker.MARKER_DESCRIPTION} とは別の文字列にする。</strong>
 * 起動時に投入するデータ（マニュアルの図と対応する固定のデータ）を、
 * 自動実行の片付けで巻き込んで消してはならない。
 */
final class DemoMark {

    /**
     * 契約番号の接頭辞。<strong>これが削除の起点である。</strong>
     *
     * <p>契約番号は法人荷主にしか無く、業務の利用者がこの接頭辞を打ち込むことは実際上ない。
     */
    static final String CONTRACT_PREFIX = "DEMO-";

    /** 自動実行が作った貨物の品名。<strong>一覧で見分けるための印である。</strong> */
    static final String AUTOPILOT_DESCRIPTION = "自動実行デモの貨物";

    private DemoMark() {
    }

    /** 契約番号を組み立てる。 */
    static String contractNumber(String suffix) {
        return CONTRACT_PREFIX + suffix;
    }
}
