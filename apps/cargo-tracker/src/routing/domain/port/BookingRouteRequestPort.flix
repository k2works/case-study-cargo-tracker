///
/// 予約からの経路設計依頼を引く ACL ポート（US08）。
///
/// **Routing Context は Booking Context に直接依存しない**。
/// [ドメインモデル設計](../../../../../../docs/design/domain-model.md) の
/// ビジネスルール 9（Booking → Shipper）と同じ形を、向きを変えて適用する。
/// 実装（`RoutingJdbcBookingRouteRequest`）が `cargo` テーブルを 1 文で引き、
/// **Routing の言葉へ翻訳して**返す。ドメイン層は `BookingModel` を知らない。
///
/// 翻訳の中身は 2 つある。
///
/// | Booking の言葉 | Routing の言葉 |
/// | :--- | :--- |
/// | `cargo_type`（この貨物は何か） | `CargoCapability`（この航海は何を運べるか） |
/// | `booking_status`（予約の状態） | 依頼として成立するか（`None` を返すか否か） |
///
/// 前者は IT6 で「同じ語だが文脈が違う」として分けた 2 つの列挙であり、
/// **その変換をここに閉じ込める**のが ACL の役目である。
///
mod RoutingBookingRouteRequest {
    use RoutingModel.CargoCapability

    ///
    /// 経路設計の依頼。
    ///
    /// `arrivalDeadline` は `YYYY-MM-DD`（`cargo.arrival_deadline` は `DATE`）。
    /// 時刻を持たない点が到着時刻との比較で効くため、型ではなく**桁で**表す
    /// （`RoutingRouteSpec` が同じ規約で受け取る）。
    ///
    pub enum RouteRequest({
        bookingId = String,
        origin = String,
        destination = String,
        arrivalDeadline = String,
        cargoTypeLabel = String,
        required = CargoCapability
    })

    ///
    /// 経路設計依頼の照会。
    ///
    pub eff BookingRouteRequest {
        ///
        /// 予約番号から依頼を引く。
        ///
        /// **経路設計者へ引き渡し済み（`ROUTE_PROPOSED`）でなければ `None`** を返す。
        /// 「存在しない」と「まだ引き渡されていない」を呼び出し側で区別させない。
        /// 区別できる形にすると、画面が 404 と 409 を出し分けることになり、
        /// **経路設計者にとってはどちらも「今は作業できない」**でしかない。
        ///
        def findRouteRequest(bookingId: String): Option[RouteRequest]
    }

    pub def bookingIdOf(r: RouteRequest): String =
        let RouteRequest.RouteRequest(v) = r; v#bookingId

    pub def originOf(r: RouteRequest): String =
        let RouteRequest.RouteRequest(v) = r; v#origin

    pub def destinationOf(r: RouteRequest): String =
        let RouteRequest.RouteRequest(v) = r; v#destination

    pub def arrivalDeadlineOf(r: RouteRequest): String =
        let RouteRequest.RouteRequest(v) = r; v#arrivalDeadline

    pub def cargoTypeLabelOf(r: RouteRequest): String =
        let RouteRequest.RouteRequest(v) = r; v#cargoTypeLabel

    pub def requiredOf(r: RouteRequest): CargoCapability =
        let RouteRequest.RouteRequest(v) = r; v#required

    /// 依頼を組み立てる（ACL 実装とテストのスタブが使う）
    pub def routeRequest(bookingId: String, origin: String, destination: String,
                         arrivalDeadline: String, cargoTypeLabel: String,
                         required: CargoCapability): RouteRequest =
        RouteRequest.RouteRequest({
            bookingId = bookingId, origin = origin, destination = destination,
            arrivalDeadline = arrivalDeadline, cargoTypeLabel = cargoTypeLabel,
            required = required
        })
}
