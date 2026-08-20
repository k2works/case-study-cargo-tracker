package com.example.bookingms.domain.model;

import java.util.Arrays;
import java.util.List;

/**
 * 危険物クラス（国連分類 1〜9）。
 *
 * <p>法定の分類であり、荷主や営業担当者が言葉を選べる項目ではない。自由入力にすると
 * 「Class 3」「3類」「引火性液体」が同じ意味で混ざり、経路設計（どの航海が危険物を運べるか）と
 * 荷役（どこに置くか）が分類で判断できなくなる。一覧から選ぶ形にする。
 *
 * <p>細分（3.1 など）は扱わない。輸送手段の可否は大分類で決まり、細分は申告書の記載事項として
 * 正式品名・UN 番号が担う。
 */
public enum HazardClass {
    CLASS_1("1", "火薬類"),
    CLASS_2("2", "高圧ガス"),
    CLASS_3("3", "引火性液体"),
    CLASS_4("4", "可燃性物質（可燃性固体・自然発火性物質・水反応可燃性物質）"),
    CLASS_5("5", "酸化性物質・有機過酸化物"),
    CLASS_6("6", "毒物・病毒をうつしやすい物質"),
    CLASS_7("7", "放射性物質"),
    CLASS_8("8", "腐食性物質"),
    CLASS_9("9", "有害性物質"),

    /**
     * 一覧に無い値で保存された古い行のための受け皿。
     *
     * <p>危険物クラスを自由入力にしていた頃の行が読めなくなると、その予約の詳細も一覧も開けない。
     * 復元のときだけこれになる。<strong>新規に選ぶことはできない</strong>（{@link #selectable()}
     * に含めない）ため、次に編集するときに正しい分類へ直る。
     */
    UNKNOWN("", "（分類不明。選び直してください）");

    private final String code;
    private final String label;

    HazardClass(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 新規の申告で受け入れる。一覧に無い値は拒否する。
     *
     * <p>名簿方式の検査で「載っていないものを通す」と、載せ忘れたものほど漏れる。
     * ここは逆に、載っていないものを必ず落とす。
     */
    public static HazardClass of(String code) {
        return Arrays.stream(selectable())
                .filter(hazardClass -> hazardClass.code.equals(trim(code)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("危険物クラスは一覧から選んでください"));
    }

    /**
     * 永続化された行から復元する。ここでは検査しない。
     *
     * <p>検査を後から足すと、その規則が無かったころの行が読めなくなる。自由入力だった頃の値は
     * {@link #UNKNOWN} として読み、行そのものは開けるようにする。
     */
    public static HazardClass restore(String stored) {
        String value = trim(stored);
        return Arrays.stream(values())
                .filter(hazardClass -> hazardClass != UNKNOWN && hazardClass.code.equals(value))
                .findFirst()
                .orElse(UNKNOWN);
    }

    /** 画面の選択肢。UNKNOWN は選ばせない。 */
    public static HazardClass[] selectable() {
        return Arrays.stream(values()).filter(hazardClass -> hazardClass != UNKNOWN)
                .toArray(HazardClass[]::new);
    }

    public static List<HazardClass> selectableList() {
        return List.of(selectable());
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
