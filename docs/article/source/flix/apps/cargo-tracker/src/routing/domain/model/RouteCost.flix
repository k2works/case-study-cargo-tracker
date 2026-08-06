///
/// 経路候補の概算費用（Routing Context）。
///
/// # なぜドメインに置くか（IT9・IT8 レビュー M5）
///
/// IT8 は同じ計算を `RoutePages.costText`（interfaces 層）に置いていた。
/// Estimation は `EstimationFreightRate`（domain 層）に置いており、
/// **同じ業務判断が、片方は domain・片方は presentation にある**非対称だった。
///
/// 非対称そのものより問題なのは、**presentation に置いた計算は
/// 画面を描くときにしか走らない**ことである。IT9 で経路を確定して予約に
/// 紐付けるとき「確定時の金額を残す」要求が出れば、presentation の計算は
/// 必ず domain へ引き上げ直すことになる。**引き上げは早いほうが安い**。
///
/// # 計算式は共有カーネルにある
///
/// 運賃表は全社で 1 つである（[`SharedFreightTariff`](../../../shared/domain/model/FreightTariff.flix)）。
/// **本モジュールに残るのは「Routing が持っている情報を運賃表へ渡す」ことだけ**であり、
/// 単価も割増率も持たない。Estimation の `FreightRate` と同じ形である。
///
mod RoutingRouteCost {
    use RoutingRouteFinder.RouteCandidate
    use SharedFreightTariff.TariffClass
    use SharedMoney.Money

    ///
    /// 候補 1 件の概算費用を求める。
    ///
    /// **区間数は候補から数える**。積替のたびに荷役と書類が発生するため、
    /// 直行より積替のある候補のほうが高くなる（共有カーネルの計算式）。
    ///
    /// 運賃区分と重量は依頼（`RouteRequest`）が持つ。**`CargoCapability` から
    /// 運賃区分を導かない**——「運べるか」と「いくらか」は別の判断であり、
    /// いまは 1 対 1 に見えても、いずれ対応が崩れる。
    ///
    pub def quoteFor(tariffClass: TariffClass, weightGrams: Int64,
                     candidate: RouteCandidate): Money =
        SharedFreightTariff.quote(tariffClass, weightGrams,
                                  List.length(RoutingRouteFinder.legsOf(candidate)))
}
