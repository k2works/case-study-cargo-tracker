/// 違反例: 配線が他 BC の効果を**名前で**書いている。
///
/// 正例（`rule11-wiring-single-context.flix`）との違いは
/// `BookingItineraryAssignmentAdapter.Requires` を `CargoRepo` に開いた 1 点だけである。
/// **型別名で隠す設計が規約 11 を通す唯一の道**であることを、この対で固定する。
mod RoutingWiring {
    use CargoRepo.CargoRepo
    use RoutingJdbcVoyageRepo.withJdbcVoyageRepo
    use VoyageRepo.VoyageRepo

    pub def withDb(f: Unit -> a \ ef + VoyageRepo):
            a \ (ef - VoyageRepo) + CargoRepo + SharedDbTx.Tx + IO =
        withJdbcVoyageRepo(f)
}
