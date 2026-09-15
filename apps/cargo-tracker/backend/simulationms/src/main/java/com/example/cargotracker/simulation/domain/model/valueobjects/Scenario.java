package com.example.cargotracker.simulation.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.util.Arrays;
import java.util.List;

/**
 * 業務シナリオ（US33 §受入基準 1）。
 *
 * <p><b>工程の並びはシナリオが持つ。</b> 実行が並びを組み立てると、シナリオを
 * 足すたびに実行の側を直すことになる。</p>
 *
 * <p><b>名簿に無いものは通さない。</b> 「知らないシナリオ」を素通りさせると、
 * 打ち間違いが「工程 0 件で成功」として記録される——何も確かめていないのに
 * 緑になる（名簿方式の検査は載っていないものを通す、の裏返し）。</p>
 */
public enum Scenario {

    /** 一般貨物が予約から精算まで通る。**US33 の中核**。 */
    STANDARD("一般貨物の標準輸送", List.of(
            StepKind.REGISTER_SHIPPER,
            StepKind.REGISTER_BOOKING,
            StepKind.REQUEST_ROUTING,
            StepKind.ASSIGN_ROUTE,
            StepKind.NOTIFY_SHIPPER,
            StepKind.CONFIRM_BOOKING,
            StepKind.ISSUE_TRACKING_NUMBER,
            StepKind.RECORD_HANDLING,
            StepKind.CLEAR_CUSTOMS,
            StepKind.CLAIM_CARGO,
            StepKind.CALCULATE_INVOICE,
            StepKind.ISSUE_INVOICE,
            StepKind.RECORD_PAYMENT)),

    /**
     * 便が 1 本も通っていない港へ運ぼうとして、経路の確定で止まる。
     *
     * <p><b>失敗する経路も 1 本要る。</b> 成功しか流せないと、「どの工程で
     * 止まったか」を出す仕組み（US34）が確かめられない。</p>
     *
     * <p><b>止め方は時間でなく構造で決める。</b> 当初は「期限を 1 日にすれば
     * 候補が無くなる」と考えたが、<b>クラスタには過去の実行が登録した便が溜まる</b>
     * ので、たまたま間に合う便があると成功してしまう（IT16 で実測）。
     * どの便も寄らない港なら、必ず同じところで止まる。</p>
     */
    NO_ROUTE("便が通わない港への輸送", List.of(
            StepKind.REGISTER_SHIPPER,
            StepKind.REGISTER_BOOKING,
            StepKind.REQUEST_ROUTING,
            StepKind.ASSIGN_ROUTE)),

    /**
     * 遅延が起きて、対応して、解決する（US35 §受入基準 1・2）。
     *
     * <p><b>例外の 4 種類は工程の並びが同じで、違うのは種別だけである</b>
     * （IT17 の注 N10）。種類ごとに {@link StepKind} を足すと列挙が肥大し、
     * 扱っていない場所が名乗り出ないまま増える。</p>
     */
    DELAY("遅延の発生と対応", exceptionSteps(), "DELAY"),

    /** 破損が起きて、対応して、解決する（US35 §受入基準 1・2）。 */
    DAMAGE("破損の発生と対応", exceptionSteps(), "DAMAGE"),

    /**
     * 誤配が起きて、**現在地からの経路が組み直されて輸送が再開する**
     * （US35 §受入基準 3）。
     *
     * <p><b>解決の前に経路を確定し直す。</b> 誤配は「どこに在るか」が変わった
     * ことなので、対応の中身は経路の組み直しである——解決だけして経路を
     * そのままにすると、同じ港へもう一度運ぶ。</p>
     */
    MISROUTE("誤配の発生と経路の組み直し", List.of(
            StepKind.REGISTER_SHIPPER,
            StepKind.REGISTER_BOOKING,
            StepKind.REQUEST_ROUTING,
            StepKind.ASSIGN_ROUTE,
            StepKind.NOTIFY_SHIPPER,
            StepKind.CONFIRM_BOOKING,
            StepKind.ISSUE_TRACKING_NUMBER,
            StepKind.REGISTER_EXCEPTION,
            StepKind.RESPOND_TO_EXCEPTION,
            // **現在地からの組み直し。** 叩く API は経路の確定と同じだが、
            // 待つ相手が違う（前と違う旅程になったか）——工程を分けないと
            // 2 度目の待ちが空振りする。
            StepKind.REASSIGN_ROUTE,
            StepKind.RESOLVE_EXCEPTION), "MISROUTE"),

    /** 税関保留が起きて、対応して、解決する（US35 §受入基準 1・2）。 */
    CUSTOMS_HOLD("税関保留の発生と対応", exceptionSteps(), "CUSTOMS_HOLD"),

    /**
     * 輸送中にキャンセルされ、**承認で指定した港での荷降しまで実行され、
     * 追跡が閉じる**（US35 §受入基準 4）。
     *
     * <p><b>承認だけでは追跡は閉じない。</b> 貨物はまだ船の上にあるので、
     * 指定された港で荷降しを記録して初めて閉じる（[ADR-0018]）。
     * 「承認したら終わり」にすると、確かめたい連鎖の最後が抜ける。</p>
     */
    CANCEL_IN_TRANSIT("輸送中のキャンセルと指定港での荷降し", List.of(
            StepKind.REGISTER_SHIPPER,
            StepKind.REGISTER_BOOKING,
            StepKind.REQUEST_ROUTING,
            StepKind.ASSIGN_ROUTE,
            StepKind.NOTIFY_SHIPPER,
            StepKind.CONFIRM_BOOKING,
            StepKind.ISSUE_TRACKING_NUMBER,
            StepKind.REQUEST_CANCELLATION,
            StepKind.APPROVE_CANCELLATION,
            StepKind.DISCHARGE_CANCELLED), null);

    /**
     * 例外の起票・対応・解決までの並び（注 N10）。
     *
     * <p><b>4 つのシナリオが同じ並びを使う。</b> 書き写すと、1 つ直したときに
     * 残り 3 つが古いままになる。</p>
     */
    private static List<StepKind> exceptionSteps() {
        return List.of(
                StepKind.REGISTER_SHIPPER,
                StepKind.REGISTER_BOOKING,
                StepKind.REQUEST_ROUTING,
                StepKind.ASSIGN_ROUTE,
                StepKind.NOTIFY_SHIPPER,
                StepKind.CONFIRM_BOOKING,
                StepKind.ISSUE_TRACKING_NUMBER,
                StepKind.REGISTER_EXCEPTION,
                StepKind.RESPOND_TO_EXCEPTION,
                StepKind.RESOLVE_EXCEPTION);
    }

    private final String label;
    private final List<StepKind> steps;
    private final String exceptionType;

    Scenario(String label, List<StepKind> steps) {
        this(label, steps, null);
    }

    Scenario(String label, List<StepKind> steps, String exceptionType) {
        this.label = label;
        this.steps = steps;
        this.exceptionType = exceptionType;
    }

    /**
     * 起票する例外の種別（US35）。<b>例外を含まないシナリオでは {@code null}</b>。
     *
     * <p>trackingms の {@code ExceptionType} の呼び名をそのまま使う——境界で
     * 翻訳すると、種別が増えたときに片方だけが古くなる。</p>
     */
    public String exceptionType() {
        return exceptionType;
    }

    /** 画面に出す呼び名。 */
    public String label() {
        return label;
    }

    /** 工程の並び。<b>この順に実行する</b>。 */
    public List<StepKind> steps() {
        return steps;
    }

    /**
     * 呼び名から引く。
     *
     * <p><b>知らないものは断る。</b> 素通りさせると、打ち間違いが
     * 「工程 0 件で成功」として記録される。</p>
     */
    public static Scenario of(String label) {
        return Arrays.stream(values())
                .filter(scenario -> scenario.label.equals(label))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleViolation(
                        "知らないシナリオです: " + label + "。選べるのは "
                                + Arrays.stream(values()).map(Scenario::label).toList()
                                + " です"));
    }
}
