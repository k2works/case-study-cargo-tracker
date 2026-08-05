/// 正例: 配線は自 BC の効果だけを名前で扱い、BC 間の翻訳は `acl/` のアダプタに委ねる。
///
/// **実コード（`RoutingWiring.flix`）の形で書く**（IT9 レビュー M2）。骨だけの
/// フィクスチャでは、この規約の要点——**アダプタが必要とする他 BC の効果を
/// 型別名（`Requires`）で隠すこと**——が検査されない。
/// 型別名は `moduleIndex` に解決されないため、配線側に Booking の語彙が出ない。
mod RoutingWiring {
    use RoutingBookingRouteRequest.BookingRouteRequest
    use RoutingJdbcBookingRouteRequest.withJdbcBookingRouteRequest
    use RoutingJdbcVoyageRepo.withJdbcVoyageRepo
    use VoyageRepo.VoyageRepo

    pub type alias Eff = VoyageRepo + BookingRouteRequest
                       + RoutingBookingItineraryAssignment.BookingItineraryAssignment

    /// 戻り効果に出るのは**アダプタの型別名**であり、Booking の効果名ではない
    pub def withDb(f: Unit -> a \ ef + Eff):
            a \ (ef - Eff) + BookingItineraryAssignmentAdapter.Requires
                          + SharedDbTx.Tx + IO =
        withJdbcVoyageRepo(() ->
            withJdbcBookingRouteRequest(() ->
                BookingItineraryAssignmentAdapter.withBookingItineraryAssignment(f)))
}
