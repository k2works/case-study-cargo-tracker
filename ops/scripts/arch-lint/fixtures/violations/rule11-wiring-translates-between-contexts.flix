/// 負例: 配線ファイルが 2 つの Bounded Context を参照している（翻訳が混ざっている）
///
/// **実コードの形で書く**（IT2 ふりかえり Try）。最小の違反例だけだと
/// メタテストは緑でも実コードの違反を見逃す。これは IT8 の
/// `EstimationWiring.searchRoutes` がまさに持っていた形である。
mod EstimationWiring {
    use EstimateRepo.EstimateRepo
    use VoyageRepo.VoyageRepo

    pub def withDb(f: Unit -> a): a = f()

    /// ここが翻訳。Estimation の語彙を Routing の型へ写している
    def searchRoutes(origin: String, destination: String): List[String] =
        match RoutingModel.capabilityFromPersisted("GENERAL") {
            case None => Nil
            case Some(capability) =>
                RoutingRouteFinder.findCandidates(origin, destination, capability)
        }
}
