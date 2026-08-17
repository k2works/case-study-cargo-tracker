package com.example.cargotracker.demo;

/**
 * デモモードが 1 件の貨物に対して踏む手順。
 *
 * <p><strong>手順を並べるだけの列挙にする。</strong> 以前は実行の本体に手順が
 * 直接書かれており、<strong>何手あるかを数えた定数と実装がずれた</strong>。
 * 列挙にすれば「次の手順」も「残りいくつか」も数え直す必要がない。
 *
 * <p><strong>順番そのものが業務のルールである。</strong> 通関が下りるまで引取は
 * 登録できず、引取が済むまで請求できない。並べ替えると業務のサービスに弾かれる。
 */
enum DemoStep {

    /** この貨物のための便を作る（US24）。 */
    REGISTER_VOYAGE("航海スケジュールを登録する", "経路設計者"),

    /** 法人荷主を登録する（US02 / US03）。 */
    REGISTER_SHIPPER("法人荷主を登録する", "営業担当者"),

    /** 貨物を予約する（US04）。 */
    BOOK("貨物を予約する", "営業担当者"),

    /** 経路設計者に引き渡す（US06）。 */
    ASSIGN_TO_ROUTING("経路設計者に引き渡す", "営業担当者"),

    /** 経路候補を算出する（US08）。 */
    PROPOSE_ROUTES("経路候補を算出する", "経路設計者"),

    /** 経路を確定する（US09 / US11）。 */
    SELECT_ROUTE("経路を確定する", "経路設計者"),

    /** 予約を確定する（US13）。 */
    CONFIRM_BOOKING("予約を確定する", "営業担当者"),

    /** 追跡番号を発行する（US14）。 */
    ISSUE_TRACKING_NUMBER("追跡番号を発行する", "追跡管理者"),

    /** 荷受人を登録する（US36）。 */
    REGISTER_CONSIGNEE("荷受人を登録する", "営業担当者"),

    /** 貨物を受け取る（US15）。 */
    RECEIVE("貨物を受け取る", "荷役作業員"),

    /** 船に積み込む（US15）。 */
    LOAD("船に積み込む", "荷役作業員"),

    /** 船から降ろす（US15）。 */
    UNLOAD("船から降ろす", "荷役作業員"),

    /** 通関に搬入する（US29）。 */
    CUSTOMS("通関に搬入する", "荷役作業員"),

    /** 通関を通す（US29）。 */
    CLEAR_CUSTOMS("通関を通す", "荷役作業員"),

    /** 荷受人が引き取る（US16）。 */
    CLAIM("荷受人が引き取る", "荷役作業員"),

    /** 請求書を作成する（US21 / US22）。 */
    CALCULATE_CHARGE("請求書を作成する", "経理担当者");

    private final String title;
    private final String actor;

    DemoStep(String title, String actor) {
        this.title = title;
        this.actor = actor;
    }

    /** 画面に出す手順の名前。 */
    String title() {
        return title;
    }

    /**
     * その手順を行う担当。
     *
     * <p><strong>どのロールの仕事かを必ず出す。</strong> 画面が勝手に変わるだけでは、
     * 利用者は自分のどの画面を見ればそれが起きているのか分からない。
     */
    String actor() {
        return actor;
    }

    /** 何手目か（1 始まり）。 */
    int number() {
        return ordinal() + 1;
    }

    /** 最後の手順か。 */
    boolean isLast() {
        return ordinal() == values().length - 1;
    }

    /** 次の手順。<strong>最後の手順に次は無い。</strong> */
    DemoStep next() {
        if (isLast()) {
            throw new IllegalStateException("最後の手順に次はありません: " + this);
        }
        return values()[ordinal() + 1];
    }

    /** 手順の総数。 */
    static int count() {
        return values().length;
    }
}
