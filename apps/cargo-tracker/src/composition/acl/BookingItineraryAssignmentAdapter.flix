///
/// 旅程の割り当ての翻訳（Routing → Booking の ACL アダプタ。IT9）。
///
/// # なぜここに置くか
///
/// **BC 間の翻訳の唯一の置き場所**である（`arch-lint` 規約 11。
/// [ADR-0011](../../../../../docs/adr/ADR-0011-routing-writes-booking-through-its-aggregate.md)）。
/// `routing/` 配下に置くと規約 4 に、`*Wiring.flix` に置くと規約 11 に違反する。
///
/// # 本アダプタは判断を持たない
///
/// Routing の `AssignedLeg` を Booking の `Leg` へ写し、
/// **Booking のアプリケーションサービスを呼ぶ**だけである。
/// 旅程が妥当かは `Cargo.attachItinerary`（集約）が決める——
/// **ここで検証を書くと、集約の不変条件が 2 箇所になる**。
///
/// ADR-0009 が懸念した「書き込みの ACL は相手の不変条件を壊しうる」は、
/// **テーブルへ直接書く場合の話**である。集約を通す限り、
/// 壊れた旅程は呼び出し元が誰であっても入らない。
///
mod BookingItineraryAssignmentAdapter {
    use RoutingBookingItineraryAssignment.{BookingItineraryAssignment, AssignedLeg,
                                           AssignmentResult}
    use CargoRepo.CargoRepo

    /// 本アダプタが要求する効果（配線ファイルに Booking の効果名を書かせない）
    pub type alias Requires = CargoRepo

    pub def withBookingItineraryAssignment(f: Unit -> a \ ef + BookingItineraryAssignment):
            a \ (ef - BookingItineraryAssignment) + Requires =
        run {
            f()
        } with handler BookingItineraryAssignment {
            def assign(bookingId, legs, k) = k(assignInBooking(bookingId, legs))
        }

    def assignInBooking(bookingId: String, legs: List[AssignedLeg]):
            AssignmentResult \ CargoRepo =
        let itinerary = BookingModel.itineraryOf(legs |> List.map(toBookingLeg));
        match BookingAssignItinerary.assign(bookingId, itinerary) {
            case BookingAssignItinerary.AssignResult.Assigned(_) => AssignmentResult.Assigned
            case BookingAssignItinerary.AssignResult.NotFound    =>
                AssignmentResult.BookingNotAvailable
            case BookingAssignItinerary.AssignResult.Rejected(e) =>
                // **「予約が使えない」と「旅程が悪い」を分ける**。前者は
                // 経路設計者が予約一覧へ戻る話、後者は候補を選び直す話であり、
                // 取れる行動が違う
                match e {
                    case BookingModel.BookingError.CargoIsNotUnderRouting =>
                        AssignmentResult.BookingNotAvailable
                    case _ => AssignmentResult.ItineraryRejected
                }
        }

    /// Routing の区間を Booking の区間へ写す
    def toBookingLeg(l: AssignedLeg): BookingModel.Leg =
        BookingModel.leg(
            RoutingBookingItineraryAssignment.legVoyageNumberOf(l),
            RoutingBookingItineraryAssignment.legLoadLocationOf(l),
            RoutingBookingItineraryAssignment.legUnloadLocationOf(l),
            RoutingBookingItineraryAssignment.legLoadTimeOf(l),
            RoutingBookingItineraryAssignment.legUnloadTimeOf(l))
}
