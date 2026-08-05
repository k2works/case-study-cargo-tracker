///
/// 経路探索の翻訳（Estimation → Routing の ACL アダプタ）。
///
/// # なぜここに置くか
///
/// **BC 間の翻訳の唯一の置き場所**である（[ADR-0011](../../../../../docs/adr/ADR-0011-routing-writes-booking-through-its-aggregate.md)
/// の規約 11）。`arch-lint` は `*Wiring.flix` が 2 つ以上の BC を参照することを禁じ、
/// 本ディレクトリだけを例外とする。
///
/// [ADR-0010](../../../../../docs/adr/ADR-0010-estimation-reuses-route-finder-via-composition.md)
/// で合成ルートに翻訳を置いたとき、それは `arch-lint` 規約 4 の対象外であるため
/// **検出できない穴**になった。IT8 のクローズ時レビューで、この穴に置いたコードの
/// コメントと実装が食い違っている（所要日数が読めない候補を「落とす」と書きながら
/// 0 で埋めていた）ことが見つかっている。
///
/// **穴を塞ぐのではなく、穴に名前を付けて数えられるようにした**。
/// 本ディレクトリのファイル数が、そのまま BC 間の翻訳の件数になる。
/// **見直しの条件は単一 BC へ向かう書き込みアダプタ数で測る**
/// （ADR-0014 決定 4）。旧条件（`composition/acl/` が 5 本超）は置き換えた——
/// 総数は「連携が増えた」と「単一の集約がハブ化している」を潰す。
///
mod EstimationRouteSearchAdapter {
    use EstimationRouteSearch.{RouteSearch, FoundRoute}
    use VoyageRepo.VoyageRepo

    ///
    /// 本アダプタが要求する効果。
    ///
    /// **配線ファイルに Routing の効果名を書かせないため**に型別名で公開する。
    /// `EstimationWiring` が `VoyageRepo` を直接名指しすると、
    /// `arch-lint` 規約 11（BC の配線ファイルは 1 つの BC だけを参照する）に違反する。
    ///
    /// 翻訳が何を必要とするかは**翻訳自身が知っている**べきであり、
    /// 配線側が知る必要はない。
    ///
    pub type alias Requires = VoyageRepo + SharedClock.Clock

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

}
