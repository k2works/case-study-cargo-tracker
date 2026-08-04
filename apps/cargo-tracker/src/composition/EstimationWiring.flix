///
/// Estimation Context の配線（IT8・US01）。
///
/// 分割の意図と `src/composition/` に置く理由は [ShipperWiring](ShipperWiring.flix) を参照。
///
/// # 経路探索の翻訳は `acl/` にある
///
/// 本 BC は `RouteSearch` ポート（Routing の経路探索を引く ACL）を持つが、
/// **その実装は [`acl/EstimationRouteSearchAdapter`](acl/EstimationRouteSearchAdapter.flix) にある**。
/// 配線ファイルに BC 間の翻訳を書くと `arch-lint` 規約 11 に違反する
/// （[ADR-0011](../../../../docs/adr/ADR-0011-routing-writes-booking-through-its-aggregate.md)）。
///
/// IT8 は翻訳を本ファイルに置いていた。規約 4 の対象外であるため検出できず、
/// **そこに置いたコードのコメントと実装が食い違っていた**ことが
/// クローズ時レビューで判明した。IT9 で置き場所を分けた。
///
mod EstimationWiring {
    use EstimateRepo.EstimateRepo
    use EstimationJdbcEstimateRepo.withJdbcEstimateRepo
    use EstimationRouteSearch.{RouteSearch, FoundRoute}

    /// 本 BC が要求する効果（リポジトリ + ACL）
    pub type alias Eff = EstimateRepo + RouteSearch

    ///
    /// 本番のアダプタを適用する。
    ///
    /// **翻訳が要求する効果は `EstimationRouteSearchAdapter.Requires` にある**
    /// （航海の一覧と現在時刻）。そのため本 BC の配線は
    /// `RoutingWiring.withDb` の**内側**で適用する必要がある
    /// （`Composition` の適用順がこれを満たしている）。
    ///
    /// **効果名をここに書かない**。書くと配線ファイルが 2 つの BC を参照することになり、
    /// `arch-lint` 規約 11 に違反する（ADR-0011）。
    ///
    pub def withDb(f: Unit -> a \ ef + Eff): a \ (ef - Eff) + EstimationRouteSearchAdapter.Requires
                                              + SharedDbTx.Tx + IO =
        withJdbcEstimateRepo(() ->
            EstimationRouteSearchAdapter.withRouteSearch(f))

    /// DB を使わない経路（`TxMode.NoTx`）で適用する
    pub def withoutDb(f: Unit -> a \ ef + Eff): a \ (ef - Eff) =
        run {
            run {
                f()
            } with handler RouteSearch {
                def search(_o, _d, _a, _c, _k) = WiringGuard.noDbViolation("RouteSearch")
            }
        } with handler EstimateRepo {
            def save(_e, _k)              = WiringGuard.noDbViolation("EstimateRepo")
            def findByEstimateId(_i, _k)  = WiringGuard.noDbViolation("EstimateRepo")
            def findAll(_k)               = WiringGuard.noDbViolation("EstimateRepo")
        }

    /// 効果を「使った」ことにする（`lift` のため。常に `false` で呼ばれる）
    pub def touch(shouldRun: Bool): Unit \ Eff =
        if (shouldRun) {
            discard EstimateRepo.findAll();
            discard RouteSearch.search("", "", "", "")
        } else ()
}
