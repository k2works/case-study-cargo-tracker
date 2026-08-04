///
/// 経路候補の照会（US08）。
///
/// 読み取りのみである。**集約の状態を変えない**——経路の確定（US09・IT8）で
/// 初めて `CONFIRMED` へ遷移する。
///
/// 本サービスの責務は 3 つに限る。
///
/// 1. ACL から依頼を引く
/// 2. 依頼を探索条件（`RouteSpec`）へ写す
/// 3. 航海を集めて `RouteFinder` に渡す
///
/// **判断そのものはドメインにある**。ここに業務ルールを書かない。
///
mod RoutingFindRouteCandidates {
    use RoutingBookingRouteRequest.{BookingRouteRequest, RouteRequest}
    use RoutingRouteFinder.{RouteCandidate, findCandidates}
    use RoutingRouteSpec.{RouteSpec, RouteSpecError, routeSpec}
    use VoyageRepo.VoyageRepo

    ///
    /// 照会の結果。
    ///
    /// **候補 0 件を失敗にしない**。「条件に合う経路が無い」は正常な回答であり、
    /// 経路設計者は条件を調整するか営業へ相談する（受入基準 6）。
    /// 失敗として扱うと、画面は依頼の内容すら表示できなくなる。
    ///
    pub enum RouteCandidatesResult with Eq, ToString {
        /// 依頼が見つからない、または経路設計者へ引き渡されていない
        case RequestNotAvailable
        /// 依頼の内容が探索条件として成立しない
        case RequestIsMalformed(RouteSpecError)
        ///
        /// 照会できた。**候補は 0 件でありうる**。
        ///
        /// 真偽値は「航海が 1 件でも登録されているか」である。候補が 0 件のとき、
        /// **「航海が未登録」なのか「あるが条件に合わない」なのか**で画面の案内が
        /// 変わる（航路管理へ導くか、営業への相談へ導くか）。
        ///
        /// **画面側で数え直させない**。数え直すと同じ読み取りを 2 度行うことになり、
        /// しかも 1 度目と 2 度目の間に登録が入れば食い違う。
        /// 判定に要る事実は、判定できる場所で一緒に返す。
        ///
        case Found(Bool)
    }

    ///
    /// 予約番号から経路候補を求める。
    ///
    /// 戻り値の 3 つ組は `(結果, 依頼, 候補)` である。依頼を `Option` で返すのは、
    /// **依頼が引けたなら候補が 0 件でも画面に条件を出す**ためである。
    /// 「出発地 JPOSA・仕向地 USLAX・期限 4/15 で候補なし」と分かって初めて、
    /// 経路設計者はどの条件を緩めるかを判断できる。
    ///
    pub def findFor(bookingId: String):
            (RouteCandidatesResult, Option[RouteRequest], List[RouteCandidate])
            \ BookingRouteRequest + VoyageRepo =
        match BookingRouteRequest.findRouteRequest(bookingId) {
            case None      => (RouteCandidatesResult.RequestNotAvailable, None, Nil)
            case Some(req) =>
                match specOf(req) {
                    case Err(e)   =>
                        (RouteCandidatesResult.RequestIsMalformed(e), Some(req), Nil)
                    case Ok(spec) =>
                        // **全件を引いてから探索する**。US07 の検索は端点でしか
                        // 絞れず、寄港地を経由する組み合わせを SQL では表現できない。
                        // 到達可能性の判定はドメイン（Datalog）の責務である
                        let voyages = VoyageRepo.findAll();
                        (RouteCandidatesResult.Found(not List.isEmpty(voyages)), Some(req),
                         findCandidates(spec, voyages))
                }
        }

    ///
    /// 依頼を探索条件へ写す。
    ///
    /// **ここで形式検証を通す**。`bookingId` はパス変数として外から来るが、
    /// 出発地・仕向地・期限は DB から読んだ値である。それでも検証するのは、
    /// **DB の値が常に正しいとは限らない**ためである（IT6 で `voyage` テーブルが
    /// 設計にあって実物に無かったように、想定と実態はずれる）。
    ///
    def specOf(req: RouteRequest): Result[RouteSpecError, RouteSpec] =
        routeSpec(RoutingBookingRouteRequest.originOf(req),
                  RoutingBookingRouteRequest.destinationOf(req),
                  RoutingBookingRouteRequest.arrivalDeadlineOf(req),
                  RoutingBookingRouteRequest.requiredOf(req))
}
