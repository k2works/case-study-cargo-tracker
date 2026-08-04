///
/// 経路探索の条件（US08）。
///
/// **Booking の `RouteSpecification` を参照しない**。語は近いが文脈が違う。
/// 前者は「予約が要求する輸送の仕様」であり、後者は「経路探索へ渡す入力」である。
/// 同じ型を共有すると BC 独立性（`arch-lint` 規約 4）に反する。
/// IT6 で `CargoCapability` を Booking の `CargoType` と分けたのと同じ判断であり、
/// 予約側の項目が増えても経路探索は影響を受けない。
///
/// 予約から本型への変換は ACL（`RoutingBookingRouteRequestPort`）が担う。
///
mod RoutingRouteSpec {
    use RoutingModel.CargoCapability

    ///
    /// 探索条件が成立しない理由。
    ///
    /// **`VoyageError` と分ける**。航海の登録が失敗する理由と、
    /// 探索条件が不正である理由は別の関心である。
    ///
    pub enum RouteSpecError with Eq, ToString {
        case OriginIsMalformed
        case DestinationIsMalformed
        case EndpointsAreSame
        case DeadlineIsMalformed
    }

    ///
    /// 経路探索の条件。
    ///
    /// `arrivalDeadline` は **`YYYY-MM-DD`（日付）** である。時刻を持たない。
    /// 到着時刻（`YYYY-MM-DDTHH:MM`）との比較は**日付単位**で行う
    /// （`RoutingRouteFinder.arrivesBy`）。素朴に比較すると
    /// 「期限当日に着く便」がすべて刈られる。
    ///
    pub enum RouteSpec({
        origin = String,
        destination = String,
        arrivalDeadline = String,
        required = CargoCapability
    })

    ///
    /// 探索条件を組み立てる。
    ///
    /// **形式検証をここで済ませる**。以降の探索は文字列の妥当性を再確認しない。
    /// 外から来た値が SQL の型変換へ届く経路を断つのは、値オブジェクトを
    /// 通す設計そのものである（IT4・IT6 で 2 回、検証を通さない入力経路が生まれた）。
    ///
    pub def routeSpec(origin: String, destination: String, arrivalDeadline: String,
                      required: CargoCapability): Result[RouteSpecError, RouteSpec] =
        if (not SharedCalendarText.isUnLocode(origin))
            Err(RouteSpecError.OriginIsMalformed)
        else if (not SharedCalendarText.isUnLocode(destination))
            Err(RouteSpecError.DestinationIsMalformed)
        else if (origin == destination)
            Err(RouteSpecError.EndpointsAreSame)
        else if (not SharedCalendarText.isIsoDate(arrivalDeadline))
            Err(RouteSpecError.DeadlineIsMalformed)
        else
            Ok(RouteSpec.RouteSpec({
                origin = origin, destination = destination,
                arrivalDeadline = arrivalDeadline, required = required
            }))

    /// 出発地（UN/LOCODE）
    pub def originOf(s: RouteSpec): String =
        let RouteSpec.RouteSpec(r) = s;
        r#origin

    /// 仕向地（UN/LOCODE）
    pub def destinationOf(s: RouteSpec): String =
        let RouteSpec.RouteSpec(r) = s;
        r#destination

    /// 到着期限（`YYYY-MM-DD`）
    pub def arrivalDeadlineOf(s: RouteSpec): String =
        let RouteSpec.RouteSpec(r) = s;
        r#arrivalDeadline

    /// 必要な対応貨物種別
    pub def requiredOf(s: RouteSpec): CargoCapability =
        let RouteSpec.RouteSpec(r) = s;
        r#required

    /// 不成立理由の表示文言（利用者に列挙値を見せない）
    pub def errorLabel(e: RouteSpecError): String = match e {
        case RouteSpecError.OriginIsMalformed      => "出発地は UN/LOCODE（5 文字）で指定してください"
        case RouteSpecError.DestinationIsMalformed => "仕向地は UN/LOCODE（5 文字）で指定してください"
        case RouteSpecError.EndpointsAreSame       => "出発地と仕向地が同じです"
        case RouteSpecError.DeadlineIsMalformed    => "到着期限の日付が不正です"
    }
}
