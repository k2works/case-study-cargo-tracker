package com.example.cargotracker.booking.domain.model;

/**
 * 貨物仕様。種別・重量・寸法・個数・品名をひとまとまりで扱う。
 *
 * <p>US04 の受入基準「貨物種別・重量・寸法・個数・品名を入力できる」は、
 * 画面でもひとつの入力ブロックとして現れる（{@code ui_design.md}「貨物情報」）。
 * **5 つを個別の引数として持ち回ると、引数の順序を間違えても型が同じ限り気づけない。**
 *
 * <p><strong>種別と特別な情報の組み合わせはここが守る</strong>（US05）。
 * 「危険物なのに申告が無い」「一般貨物なのに温度条件がある」という組み合わせを作らせない。
 * <strong>DB の CHECK では書かない</strong> — 種別が増えるたびに条件が伸びて読めなくなる。
 *
 * @param cargoType   貨物種別（必須）
 * @param weight      重量（必須）
 * @param dimensions  寸法（任意。{@code null} 可）
 * @param quantity    個数（任意。{@code null} 可）
 * @param description 品名（任意。{@code null} 可）
 * @param hazardous   危険物申告。<strong>危険物では必須、それ以外では {@code null}</strong>
 * @param temperature 温度管理条件。<strong>冷凍・冷蔵では必須、それ以外では {@code null}</strong>
 */
public record CargoSpecification(
        CargoType cargoType,
        Weight weight,
        Dimensions dimensions,
        Quantity quantity,
        Description description,
        HazardousDeclaration hazardous,
        TemperatureRequirement temperature) {

    /**
     * 永続化された値から復元する。
     *
     * <p><strong>種別と申告の整合はここでは求めない。</strong> 危険物・冷凍の列が無かった
     * ころに登録された予約（IT1〜IT8）には申告が無い。整合を求めると
     * <strong>保存できたものが読めなくなり</strong>、その予約の追跡もキャンセルも
     * できなくなる。到着期限の未来日チェックを復元時に行わないのと同じ判断である。
     *
     * <p>**新しく預かるときの守りは変わらない。** 申告の無い危険物は登録できない。
     *
     * <p><strong>呼んでよいのはリポジトリの復元処理だけである。</strong>
     * 「検査を通したくない」ときの抜け道に使わない — 使えば、申告の無い危険物を
     * <strong>新しく作れてしまう</strong>。テストで危険物を組み立てるときは
     * {@link #create} に申告を渡す（申告を用意する手間こそが、
     * 業務でそれが必須であることの現れである）。
     */
    public static CargoSpecification reconstruct(
            CargoType cargoType,
            Weight weight,
            Dimensions dimensions,
            Quantity quantity,
            Description description,
            HazardousDeclaration hazardous,
            TemperatureRequirement temperature) {
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別は必須です");
        }
        if (weight == null) {
            throw new IllegalArgumentException("重量は必須です");
        }
        return new CargoSpecification(
                cargoType, weight, dimensions, quantity, description,
                cargoType == CargoType.HAZARDOUS ? hazardous : null,
                cargoType == CargoType.REFRIGERATED ? temperature : null);
    }

    public CargoSpecification {
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別は必須です");
        }
        if (weight == null) {
            throw new IllegalArgumentException("重量は必須です");
        }
        // **種別を変えた後に残った入力は捨てる。** 「危険物でないのに申告がある」形を
        // 残すと、種別で分岐する処理がどちらを信じてよいか分からなくなる
        if (cargoType != CargoType.HAZARDOUS) {
            hazardous = null;
        }
        if (cargoType != CargoType.REFRIGERATED) {
            temperature = null;
        }
    }

    /**
     * 新しく預かる貨物の仕様を作る（US04 / US05）。
     *
     * <p><strong>申告の無い危険物を預からない。</strong> 法的要件を満たさないまま
     * 輸送が始まる。冷凍・冷蔵で温度を欠くと貨物そのものが失われる。
     *
     * <p><strong>この検査は「新しく預かるとき」だけに効く。</strong> 復元
     * （{@link #reconstruct}）では求めない — 列が無かったころの予約が読めなくなる。
     */
    public static CargoSpecification create(
            CargoType cargoType,
            Weight weight,
            Dimensions dimensions,
            Quantity quantity,
            Description description,
            HazardousDeclaration hazardous,
            TemperatureRequirement temperature) {
        if (cargoType == CargoType.HAZARDOUS && hazardous == null) {
            throw new IllegalArgumentException(
                    "危険物には危険物申告（クラス・UN 番号・正式輸送品名）が必要です");
        }
        if (cargoType == CargoType.REFRIGERATED && temperature == null) {
            throw new IllegalArgumentException(
                    "冷凍・冷蔵貨物には温度管理条件（最低温度・最高温度・単位）が必要です");
        }
        return new CargoSpecification(
                cargoType, weight, dimensions, quantity, description, hazardous, temperature);
    }

    /**
     * 必須項目だけを持つ仕様を作る近道。
     *
     * <p><strong>{@link #create} に委譲する。</strong> ここが正準コンストラクタを
     * 直接呼んでいたため、{@code of(HAZARDOUS, weight)} で
     * <strong>申告の無い危険物が作れた</strong>（IT9 レビュー M2）。
     * 画面からの経路は {@code create} を通るので統合テストでは現れず、
     * 集約の守りが<strong>テストの世界でだけ無効</strong>になる形だった。
     *
     * <p>したがって<strong>この近道で作れるのは、特別な情報を要さない種別だけ</strong>である。
     * 危険物・冷凍を組み立てるときは {@code create} に申告・温度条件を渡す。
     */
    public static CargoSpecification of(CargoType cargoType, Weight weight) {
        return create(cargoType, weight, null, null, null, null, null);
    }

    /** 危険物申告を持つか。 */
    public boolean hasHazardousDeclaration() {
        return hazardous != null;
    }

    /** 温度管理条件を持つか。 */
    public boolean hasTemperatureRequirement() {
        return temperature != null;
    }
}
