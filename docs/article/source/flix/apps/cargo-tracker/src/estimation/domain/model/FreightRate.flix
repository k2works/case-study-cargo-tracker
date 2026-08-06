///
/// 見積の運賃（Estimation Context・US01）。
///
/// # 計算式そのものは共有カーネルにある
///
/// IT8 の当初は本モジュールが計算式を持っていた。US08 の受入基準 3
/// （経路候補ごとに**費用**が表示される）を満たす段になって、
/// **Routing からも同じ計算が要る**ことが分かり、
/// [`SharedFreightTariff`](../../../shared/domain/model/FreightTariff.flix) へ移した。
///
/// 運賃表は**全社で 1 つ**である。同じ貨物を、見積の画面と経路割り当ての画面で
/// 違う金額で見せることはあり得ない。移した理由の詳細は共有カーネル側に書いた。
///
/// **本モジュールに残るのは「Estimation の貨物種別を運賃区分へ写す」ことだけ**である。
/// 写像を共有カーネルに置くと、共有カーネルが各 BC の型を知ることになり、
/// 依存の向きが逆になる。
///
mod EstimationFreightRate {
    use EstimationModel.CargoKind
    use SharedFreightTariff.TariffClass
    use SharedMoney.Money

    ///
    /// 見積の貨物種別を運賃区分へ写す。
    ///
    /// 値が 1 対 1 に対応するのは、**同じ業務語彙を指しているから**であって
    /// 同じ型だからではない。種別が増えても運賃区分が増えるとは限らない
    /// （たとえば「精密機器」は標準運賃かもしれない）。
    ///
    pub def tariffClassOf(kind: CargoKind): TariffClass = match kind {
        case CargoKind.General      => TariffClass.Standard
        case CargoKind.Hazardous    => TariffClass.Hazardous
        case CargoKind.Refrigerated => TariffClass.Refrigerated
    }

    /// 概算運賃を求める（計算式は共有カーネル）
    pub def estimate(kind: CargoKind, weightGrams: Int64, legCount: Int32): Money =
        SharedFreightTariff.quote(tariffClassOf(kind), weightGrams, legCount)
}
