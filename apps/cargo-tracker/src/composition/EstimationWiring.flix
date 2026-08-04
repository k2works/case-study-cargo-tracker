///
/// Estimation Context の配線（IT8・US01）。
///
/// 分割の意図と `src/composition/` に置く理由は [ShipperWiring](ShipperWiring.flix) を参照。
///
/// # 本モジュールだけが持つ責務: 経路探索の翻訳
///
/// 他の BC の配線はアダプタを適用するだけだが、本モジュールは
/// **`RouteSearch` ポートの実装そのもの**を持つ。Routing の `RouteFinder` を
/// 再利用するためであり、`estimation/` 配下に置くと `arch-lint` 規約 4
/// （BC 間の直接参照）に違反する
/// （[ADR-0010](../../../../docs/adr/ADR-0010-estimation-reuses-route-finder-via-composition.md)）。
///
/// **`arch-lint` はこの結合を検出できない**。合成ルートは規約 4 の対象外である。
/// 見えないところに逃がしたのではなく、**見えないことを承知で置いた**——
/// 代替（探索の再実装）は、見積と経路割り当てで候補が食い違う欠陥を生む。
///
/// 翻訳が 2 件目（Handling・Billing でも同型が起きたら）になったら、
/// 合成ルートの下に `acl/` を設けて規約 4 の対象にすることを検討する。
///
mod EstimationWiring {
    use EstimateRepo.EstimateRepo
    use EstimationJdbcEstimateRepo.withJdbcEstimateRepo
    use EstimationRouteSearch.{RouteSearch, FoundRoute}
    use VoyageRepo.VoyageRepo

    /// 本 BC が要求する効果（リポジトリ + ACL）
    pub type alias Eff = EstimateRepo + RouteSearch

    ///
    /// 本番のアダプタを適用する。
    ///
    /// **`RouteSearch` は `VoyageRepo` を要求する**。そのため本 BC の配線は
    /// `RoutingWiring.withDb` の**内側**で適用する必要がある
    /// （`Composition` の適用順がこれを満たしている）。
    ///
    pub def withDb(f: Unit -> a \ ef + Eff): a \ (ef - Eff) + VoyageRepo + SharedClock.Clock
                                              + SharedDbTx.Tx + IO =
        withJdbcEstimateRepo(() ->
            withRouteSearch(f))

    ///
    /// 経路探索の実装（ACL の翻訳）。
    ///
    /// Estimation の語彙（文字列の出発地・仕向地・期限・種別）を
    /// Routing の `RouteSpec` へ写し、結果を `FoundRoute` へ戻す。
    ///
    /// **`Clock` を要求する**。`RouteSpec` は「今日より前に出発する便を
    /// 候補にしない」ため出発可能日を要る。今日がいつかを知るのは
    /// アプリケーション層の責務であり、ドメイン（`RouteFinder`）は
    /// 効果を要求しない（IT7 で確立した形）。
    ///
    pub def withRouteSearch(f: Unit -> a \ ef + RouteSearch):
            a \ (ef - RouteSearch) + VoyageRepo + SharedClock.Clock =
        run {
            f()
        } with handler RouteSearch {
            def search(origin, destination, arrivalDeadline, cargoKind, k) =
                k(searchRoutes(origin, destination, arrivalDeadline, cargoKind))
        }

    def searchRoutes(origin: String, destination: String, arrivalDeadline: String,
                     cargoKind: String): List[FoundRoute] \ VoyageRepo + SharedClock.Clock =
        // **種別の翻訳で既定値へ倒さない**。未知の種別を `General` にすると、
        // 危険物の見積に危険物を運べない便が並ぶ
        match RoutingModel.capabilityFromPersisted(cargoKind) {
            case None => Nil
            case Some(capability) =>
                match RoutingRouteSpec.routeSpec(origin, destination,
                                                 SharedClock.Clock.today(),
                                                 arrivalDeadline, capability) {
                    // 探索条件として成立しないなら候補は無い。利用者に対しては
                    // 「間に合う経路がありません」であり、区別しても行動が変わらない
                    case Err(_)   => Nil
                    case Ok(spec) =>
                        RoutingRouteFinder.findCandidates(spec, VoyageRepo.findAll())
                            |> List.filterMap(toFoundRoute)
                }
        }

    ///
    /// Routing の候補を Estimation の語彙へ戻す。
    ///
    /// **所要日数が読めない候補は 0 日にしない**（IT7 レビュー M17）。
    /// 0 日は正当に起こりうる値であり、壊れた候補が最速として先頭に出る。
    /// 読めなければ**候補ごと落とす**——見積の金額は区間数から決まるため、
    /// 日数が出せない候補を「いくら」だけ答えても営業は使えない。
    ///
    def toFoundRoute(c: RoutingRouteFinder.RouteCandidate): Option[FoundRoute] =
        RoutingRouteFinder.transitDaysOf(c) |> Option.map(days ->
            EstimationRouteSearch.foundRoute(
                RoutingRouteFinder.voyageNumbersOf(c),
                RoutingRouteFinder.transitPortsOf(c),
                days,
                List.length(RoutingRouteFinder.legsOf(c))))

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
