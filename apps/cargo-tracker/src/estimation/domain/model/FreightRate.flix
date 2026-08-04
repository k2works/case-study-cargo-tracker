///
/// 運賃の計算（Estimation Context・US01）。
///
/// # なぜ運賃モデルを見積と同時に作るか
///
/// 運賃モデルだけ先に入れる案は採らなかった（[IT8 計画](../../../../../../docs/development/iteration_plan-8.md)
/// のスコープ判断 2）。**運賃モデルは見積の受入基準から導かれる**ものであり、
/// 見積を作らずに運賃だけ定義すると「誰も使わない計算式」になる。
/// 値の無い列を出さないのと同じ理由で、**使われ方の決まっていない計算式も作らない**。
///
/// # この計算式は概算である
///
/// US01 は「荷主が予算と納期を**事前に**把握できる」ためのものであり、
/// 請求額（US21・US23）ではない。**実額との差は運用で吸収される**前提で、
/// 営業が電話口で即答できる粒度に留める。
///
/// 実際の海上運賃は航路・季節・船腹需給で動くが、それらを取り込むには
/// 外部の運賃表が要る（本システムの範囲外）。**持っていない情報を
/// 持っているかのような精度で出さない**——画面には「概算」と明示する。
///
/// # 端数の扱いを明示する
///
/// 各段階で**切り上げ**る（`SharedMoney.scaleByBasisPoints`）。
/// 切り捨てると積み上げた合計が個別の合計より小さくなり、
/// 「明細を足しても合計に合わない」が起きる。
///
mod EstimationFreightRate {
    use EstimationModel.CargoKind
    use SharedMoney.Money

    ///
    /// 基本料金（航海 1 本あたり 50,000 円）。
    ///
    /// 書類作成・港湾手続きなど、**重量に依らず発生する費用**である。
    /// 積替があれば区間ごとに発生するため、区間数を掛ける。
    ///
    pub def baseFarePerLeg(): Money = SharedMoney.ofYen(50000i64)

    ///
    /// 重量単価（1 kg あたり 120 円）。
    ///
    /// 重量はグラムで保持しているため、計算は**グラム単価**で行う。
    /// キログラムへ丸めてから掛けると、1.5 kg のような値で誤差が出る。
    ///
    pub def farePerKilogram(): Money = SharedMoney.ofYen(120i64)

    ///
    /// 貨物種別による割増（万分率）。
    ///
    /// 危険物は書類・隔離積付・保険が、冷凍は電源とリーファーの確保が要る。
    /// **一般貨物を 100%（割増なし）の基準**とする。
    ///
    pub def surchargeBasisPoints(kind: CargoKind): Int64 = match kind {
        case CargoKind.General      => 10000i64
        case CargoKind.Hazardous    => 15000i64
        case CargoKind.Refrigerated => 13000i64
    }

    ///
    /// 概算運賃を求める。
    ///
    /// `(基本料金 × 区間数 + 重量単価 × 重量) × 種別割増`
    ///
    /// **区間数を掛ける**のは、積替のたびに荷役と書類が発生するためである。
    /// 直行より積替 2 回のほうが安いという結果は、業務上あり得ない。
    ///
    /// 重量は**グラム**で受け取る（Booking の `weightGrams` と同じ表現）。
    /// 画面がキログラムを扱い、ドメインがグラムを扱う形は既存と揃えてある。
    ///
    pub def estimate(kind: CargoKind, weightGrams: Int64, legCount: Int32): Money =
        let base = SharedMoney.times(baseFarePerLeg(), Int32.toInt64(legCount));
        // グラム単価 = キログラム単価 / 1000。**先に掛けてから割る**ことで、
        // 1 kg 未満の貨物でも単価が 0 に潰れない
        let weightFare = SharedMoney.ofSen(
            SharedMoney.senOf(farePerKilogram()) * weightGrams / 1000i64);
        SharedMoney.scaleByBasisPoints(SharedMoney.add(base, weightFare),
                                       surchargeBasisPoints(kind))
}
