///
/// 運賃表（共有カーネル。IT8・US01 / US08）。
///
/// # なぜ共有カーネルなのか
///
/// 運賃表は**全社で 1 つ**である。同じ貨物を、見積の画面と経路割り当ての画面で
/// 違う金額で見せることはあり得ない。将来 US21（輸送料金の算出）・US23（精算）が
/// 参照するのも同じ表である。
///
/// IT8 の当初は Estimation Context の中に置いていた。US08 の受入基準 3
/// （経路候補ごとに**費用**が表示される）を満たす段になって、
/// **Routing からも同じ計算が要る**ことが分かった。
///
/// このとき選べたのは 3 つである。
///
/// | 案 | 判断 |
/// | :--- | :--- |
/// | Routing に運賃を再実装する | **却下**。同じ貨物が画面によって違う金額になる |
/// | Routing に ACL ポートを足し、合成ルートで Estimation を呼ぶ | **却下**。[ADR-0010](../../../../../../docs/adr/ADR-0010-estimation-reuses-route-finder-via-composition.md) の翻訳が 2 件目になる。ADR 自身が「2 件目なら置き場所を見直す」と書いている |
/// | 共有カーネルへ出す（採用） | 運賃表はどのコンテキストでも同じ意味を持つ。`Location`・`Money` と同じ性質である |
///
/// **共有カーネルは小さく保つ**という原則には反しない。ここに置くのは
/// 「どのコンテキストでも同じ意味を持つもの」だけであり、
/// 運賃表はまさにそれである（経路探索は Routing 固有なので出さなかった）。
///
/// # この計算式は概算である
///
/// 実際の海上運賃は航路・季節・船腹需給で動くが、それらを取り込むには
/// 外部の運賃表が要る（本システムの範囲外）。
/// **持っていない情報を持っているかのような精度で出さない**——
/// 画面には「概算」と明示する。
///
mod SharedFreightTariff {
    use SharedMoney.Money

    ///
    /// 運賃区分。
    ///
    /// 各コンテキストは**自分の貨物種別をここへ写す**
    /// （Estimation の `CargoKind`・Routing の `CargoCapability`）。
    /// 共有カーネルが各 BC の型を知る形にすると、依存の向きが逆になる。
    ///
    pub enum TariffClass with Eq, ToString {
        case Standard
        case Hazardous
        case Refrigerated
    }

    ///
    /// 基本料金（航海 1 本あたり 50,000 円）。
    ///
    /// 書類作成・港湾手続きなど、**重量に依らず発生する費用**である。
    /// 積替があれば区間ごとに発生するため、区間数を掛ける。
    ///
    pub def baseFarePerLeg(): Money = SharedMoney.ofYen(50000i64)

    /// 重量単価（1 kg あたり 120 円）
    pub def farePerKilogram(): Money = SharedMoney.ofYen(120i64)

    ///
    /// 区分による割増（万分率）。
    ///
    /// 危険物は書類・隔離積付・保険が、冷凍は電源とリーファーの確保が要る。
    /// **標準を 100%（割増なし）の基準**とする。
    ///
    pub def surchargeBasisPoints(c: TariffClass): Int64 = match c {
        case TariffClass.Standard     => 10000i64
        case TariffClass.Hazardous    => 15000i64
        case TariffClass.Refrigerated => 13000i64
    }

    ///
    /// 概算運賃を求める。
    ///
    /// `(基本料金 × 区間数 + 重量単価 × 重量) × 区分割増`
    ///
    /// **区間数を掛ける**のは、積替のたびに荷役と書類が発生するためである。
    /// 直行より積替 2 回のほうが安いという結果は、業務上あり得ない。
    ///
    /// 重量は**グラム**で受け取る（`cargo.weight` の内部表現と同じ）。
    /// 端数は各段階で切り上げる（`SharedMoney.scaleByBasisPoints`）。
    ///
    pub def quote(c: TariffClass, weightGrams: Int64, legCount: Int32): Money =
        let base = SharedMoney.times(baseFarePerLeg(), Int32.toInt64(legCount));
        // グラム単価 = キログラム単価 / 1000。**先に掛けてから割る**ことで、
        // 1 kg 未満の貨物でも単価が 0 に潰れない
        let weightFare = SharedMoney.ofSen(
            SharedMoney.senOf(farePerKilogram()) * weightGrams / 1000i64);
        SharedMoney.scaleByBasisPoints(SharedMoney.add(base, weightFare),
                                       surchargeBasisPoints(c))
}
