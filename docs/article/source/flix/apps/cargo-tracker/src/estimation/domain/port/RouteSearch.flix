///
/// 経路探索（ACL ポート。US01・IT8）。
///
/// **Estimation の語彙だけで定義する**。`RouteSpec` や Routing の
/// `RouteCandidate` は現れない。現れると、Estimation のドメイン層が
/// Routing のモデルに依存し、ACL を置いた意味が消える
/// （[ADR-0010](../../../../../../docs/adr/ADR-0010-estimation-reuses-route-finder-via-composition.md)）。
///
/// 実装は**合成ルート**（`src/composition/EstimationWiring.flix`）が与える。
/// Routing の `RouteFinder` を呼ぶ翻訳であり、`estimation/` 配下に置くと
/// `arch-lint` 規約 4（BC 間の直接参照）に違反する。
///
/// # なぜ探索を再実装しないか
///
/// 積替回数の上限・時刻の接続・出発日の絞り込みは、いずれも**業務ルールとして 1 つ**である。
/// 2 箇所に書くと、片方だけ直したときに**見積で出した候補と経路割り当てで出る候補が
/// 食い違う**。営業が「3 日で着きます」と答えた後、経路設計者の画面には
/// 該当する便が無い——どちらの画面も単体では正しく動くため気付けない。
///
mod EstimationRouteSearch {

    ///
    /// 探索の結果 1 件。
    ///
    /// **金額を含まない**。運賃は Estimation の関心であり（`FreightRate`）、
    /// Routing は金額を知らない。ここに金額を持たせると、
    /// 運賃の改定が Routing の変更に見えてしまう。
    ///
    pub enum FoundRoute({
        voyageNumbers = List[String],
        transitPorts = List[String],
        transitDays = Int32,
        legCount = Int32
    })

    pub def foundRoute(voyageNumbers: List[String], transitPorts: List[String],
                       transitDays: Int32, legCount: Int32): FoundRoute =
        FoundRoute.FoundRoute({
            voyageNumbers = voyageNumbers, transitPorts = transitPorts,
            transitDays = transitDays, legCount = legCount
        })

    pub def routeVoyageNumbersOf(r: FoundRoute): List[String] =
        let FoundRoute.FoundRoute(x) = r; x#voyageNumbers

    pub def routeTransitPortsOf(r: FoundRoute): List[String] =
        let FoundRoute.FoundRoute(x) = r; x#transitPorts

    pub def routeTransitDaysOf(r: FoundRoute): Int32 =
        let FoundRoute.FoundRoute(x) = r; x#transitDays

    pub def routeLegCountOf(r: FoundRoute): Int32 =
        let FoundRoute.FoundRoute(x) = r; x#legCount

    pub eff RouteSearch {
        ///
        /// 条件に合う経路を探す。
        ///
        /// `cargoKind` は永続化値（`GENERAL` 等）で渡す。**列挙型を渡さない**のは、
        /// Estimation の `CargoKind` を合成ルートが Routing の `CargoCapability` へ
        /// 翻訳するためであり、その翻訳の入力は文字列で十分だからである。
        ///
        /// 該当が無ければ空リスト。**「探せなかった」と「無かった」を
        /// 区別しない**——利用者に対してはどちらも「間に合う経路がありません」であり、
        /// 区別しても取れる行動が変わらない。
        ///
        def search(origin: String, destination: String, arrivalDeadline: String,
                   cargoKind: String): List[FoundRoute]
    }
}
