package com.example.cargotracker.demo;

import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import java.math.BigDecimal;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 自動実行 1 回分の条件。<strong>毎回ちがう貨物を流す</strong>ための材料である。
 *
 * <p><strong>この貨物のための便を、実行のたびに新しく登録する。</strong> 起動時に
 * 投入した 4 便に相乗りさせると、<strong>割り当てるたびに空き容量が減り、
 * いずれ経路候補が 0 件になる</strong>。デモを繰り返すほど動かなくなる。
 * そのため区間と貨物種別は自由に選んでよく、便のほうを合わせて作る。
 *
 * <p><strong>荷主の条件と輸送の条件を分けて持つ。</strong> 一列に並べると
 * 同じ型の文字列が 5 つ以上続き、<strong>位置を取り違えてもコンパイルが通る</strong>（C3）。
 *
 * @param corporate 法人荷主の条件
 * @param shipment  輸送の条件
 */
record DemoScenario(Corporate corporate, Shipment shipment) {

    /**
     * 法人荷主の条件。
     *
     * @param name           法人の名前
     * @param contractSuffix 契約番号の連番部分
     * @param discountRate   契約割引率（0.00〜0.30）
     */
    record Corporate(String name, String contractSuffix, BigDecimal discountRate) {
    }

    /**
     * 輸送の条件。
     *
     * @param origin         出発港
     * @param destination    到着港
     * @param voyage         この貨物のために登録する便の番号
     * @param cargoType      貨物種別
     * @param weightKg       重量（kg）
     * @param deadlineInDays 到着期限までの日数
     */
    record Shipment(
            String origin, String destination, String voyage,
            CargoType cargoType, String weightKg, int deadlineInDays) {
    }

    /** 法人の名前に使う語。 */
    private static final List<String> NAME_HEADS = List.of(
            "東雲", "青葉", "北斗", "南風", "明星", "白鳳", "海神", "暁", "翡翠", "颯");

    private static final List<String> NAME_TAILS = List.of(
            "商事", "物産", "貿易", "海運", "産業", "通商");

    /**
     * 流す区間。<strong>便はこの区間に合わせて新しく登録する。</strong>
     *
     * @param origin      出発港
     * @param destination 到着港
     */
    private record Lane(String origin, String destination) {
    }

    private static final List<Lane> LANES = List.of(
            new Lane(DemoVoyageSteps.OSAKA, DemoVoyageSteps.LOS_ANGELES),
            new Lane(DemoVoyageSteps.KOBE, DemoVoyageSteps.ROTTERDAM),
            new Lane(DemoVoyageSteps.YOKOHAMA, DemoVoyageSteps.HAMBURG),
            new Lane(DemoVoyageSteps.OSAKA, DemoVoyageSteps.SINGAPORE));

    /** 流す貨物種別。<strong>便のほうを合わせて作るので、どれを選んでもよい。</strong> */
    private static final List<CargoType> CARGO_TYPES = List.of(CargoType.values());

    /**
     * 条件をひと組つくる。
     *
     * @param random   乱数（テストから固定できるように受け取る）
     * @param sequence 契約番号の連番（同じ番号の契約を 2 つ作らないため）
     */
    static DemoScenario random(RandomGenerator random, long sequence) {
        Lane lane = LANES.get(random.nextInt(LANES.size()));
        CargoType cargoType = CARGO_TYPES.get(random.nextInt(CARGO_TYPES.size()));
        String name = NAME_HEADS.get(random.nextInt(NAME_HEADS.size()))
                + NAME_TAILS.get(random.nextInt(NAME_TAILS.size()));
        // 割引率は 0%〜30%（DiscountRate の値域）。5% 刻みにして請求書で読みやすくする
        BigDecimal discount = new BigDecimal(random.nextInt(7) * 5).movePointLeft(2);
        return new DemoScenario(
                new Corporate(name, "%05d".formatted(sequence), discount),
                new Shipment(
                        lane.origin(), lane.destination(),
                        // **便の番号も実行ごとに変える。** 同じ番号は登録できない
                        "DEMO-%05d".formatted(sequence), cargoType,
                        // 500kg〜4,500kg を 100kg 刻みで
                        String.valueOf((5 + random.nextInt(41)) * 100),
                        // 到着期限は便の到着（最長 36 日後）より後でなければ候補が 0 件になる
                        45 + random.nextInt(30)));
    }

    /** 契約番号（印つき）。 */
    String contractNumber() {
        return DemoMark.contractNumber(corporate().contractSuffix());
    }

    /** 割引率を百分率の文字列にする（画面と請求の説明に使う）。 */
    String discountPercentage() {
        return corporate().discountRate().movePointRight(2).stripTrailingZeros().toPlainString();
    }

    /** 画面に出す条件の要約。 */
    String summary() {
        return "%s（割引 %s%%）／ %s → %s ／ %s %skg ／ 期限 %d 日後".formatted(
                corporate().name(), discountPercentage(),
                shipment().origin(), shipment().destination(),
                shipment().cargoType().displayName(),
                shipment().weightKg(), shipment().deadlineInDays());
    }
}
