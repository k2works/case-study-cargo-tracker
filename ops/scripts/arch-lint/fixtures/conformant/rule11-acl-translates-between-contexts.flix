/// 正例: `composition/acl/` に置いた翻訳は適法（ADR-0011 の規約 11）
///
/// **ここが BC 間の翻訳の唯一の置き場所**である。ファイル数がそのまま
/// 翻訳の件数になり、穴が数えられる。
mod EstimationRouteSearchAdapter {
    use EstimationRouteSearch.RouteSearch
    use VoyageRepo.VoyageRepo

    pub def withRouteSearch(f: Unit -> a): a =
        match RoutingModel.capabilityFromPersisted("GENERAL") {
            case None    => f()
            case Some(_) => f()
        }
}
