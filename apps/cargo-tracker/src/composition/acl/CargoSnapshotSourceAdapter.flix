///
/// 貨物の写しの取得の翻訳（Handling → Booking の ACL アダプタ。IT10・US15）。
///
/// **BC 間の翻訳の唯一の置き場所**である（`arch-lint` 規約 11・ADR-0012）。
///
/// Booking の `Cargo` と `CargoItinerary` を、Handling の `CargoSnapshot` へ写す。
/// **持たせるのは荷役の妥当性判定に要る値だけ**——荷主・貨物種別・金額は写さない。
/// 写せる形にすると、いずれ「ついでに荷主名も」と越境が始まる。
///
mod CargoSnapshotSourceAdapter {
    use CargoRepo.CargoRepo
    use HandlingCargoSnapshotSource.CargoSnapshotSource
    use HandlingModel.CargoSnapshot

    /// 本アダプタが要求する効果（配線ファイルに Booking の効果名を書かせない）
    pub type alias Requires = CargoRepo

    pub def withCargoSnapshotSource(f: Unit -> a \ ef + CargoSnapshotSource):
            a \ (ef - CargoSnapshotSource) + Requires =
        run {
            f()
        } with handler CargoSnapshotSource {
            def findByTrackingNumber(trackingNumber, k) = k(snapshotOf(trackingNumber))
        }

    def snapshotOf(trackingNumber: String): Option[CargoSnapshot] \ CargoRepo =
        match CargoRepo.findByTrackingNumber(trackingNumber) {
            case None    => None
            case Some(c) =>
                let spec = BookingModel.routeSpecificationOf(c);
                Some(HandlingModel.cargoSnapshot(
                    BookingModel.originOf(spec),
                    BookingModel.destinationOf(spec),
                    legsOf(c)))
        }

    /// 旅程の区間を写す。未割り当てなら空リスト
    def legsOf(c: BookingModel.Cargo): List[HandlingModel.LegSnapshot] =
        match BookingModel.cargoItineraryOf(c) {
            case None    => Nil
            case Some(i) =>
                BookingModel.legsOf(i) |> List.map(l ->
                    HandlingModel.legSnapshot(
                        BookingModel.legLoadLocationOf(l),
                        BookingModel.legUnloadLocationOf(l),
                        BookingModel.legVoyageNumberOf(l)))
        }
}
