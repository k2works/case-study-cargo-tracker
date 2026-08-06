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
    use SharedClock.Clock
    use VoyageRepo.VoyageRepo

    ///
    /// 照会の結果。
    ///
    /// **候補 0 件を失敗にしない**。「条件に合う経路が無い」は正常な回答であり、
    /// 経路設計者は条件を調整するか営業へ相談する（受入基準 6）。
    /// 失敗として扱うと、画面は依頼の内容すら表示できなくなる。
    ///
    /// **`Eq` / `ToString` を導出しない**。レコードを持つ列挙は Flix が導出できず、
    /// また「結果が等しい」ことを比べたい場面が無い（画面はどの枝かで分岐する）。
    /// 判別が要る場合は `match` で分ける
    pub enum RouteCandidatesResult {
        /// 依頼が見つからない、または経路設計者へ引き渡されていない
        case RequestNotAvailable
        /// 依頼の内容が探索条件として成立しない
        case RequestIsMalformed(RouteRequest, RouteSpecError)
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
        case Found({ request = RouteRequest, hasVoyages = Bool,
                     candidates = List[RouteCandidate] })
    }

    ///
    /// 予約番号から経路候補を求める。
    ///
    /// **結果を 1 つの値で返す**（IT8・TS14。IT7 レビュー M10）。
    ///
    /// IT7 は `(結果, 依頼, 候補)` の 3 つ組だった。**不正な組み合わせを
    /// 型で表現できてしまう**——`RequestNotAvailable` なのに依頼が `Some`、
    /// `Found` なのに依頼が `None`、といった状態が作れる。実際 `RouteRoutes` は
    /// `(結果, 依頼)` の組で `match` し、**あり得ない組を `case _` で
    /// まとめて握り潰していた**。あり得ないなら型で消す。
    ///
    /// 依頼を `Found` と `RequestIsMalformed` の両方に持たせるのは、
    /// **依頼が引けたなら候補が 0 件でも画面に条件を出す**ためである。
    /// 「出発地 JPOSA・仕向地 USLAX・期限 4/15 で候補なし」と分かって初めて、
    /// 経路設計者はどの条件を緩めるかを判断できる。
    ///
    pub def findFor(bookingId: String):
            RouteCandidatesResult \ BookingRouteRequest + VoyageRepo + Clock =
        match BookingRouteRequest.findRouteRequest(bookingId) {
            case None      => RouteCandidatesResult.RequestNotAvailable
            case Some(req) =>
                match specOf(req) {
                    case Err(e)   => RouteCandidatesResult.RequestIsMalformed(req, e)
                    case Ok(spec) =>
                        // **全件を引いてから探索する**。US07 の検索は端点でしか
                        // 絞れず、寄港地を経由する組み合わせを SQL では表現できない。
                        // 到達可能性の判定はドメイン（Datalog）の責務である
                        let voyages = VoyageRepo.findAll();
                        RouteCandidatesResult.Found({
                            request = req,
                            hasVoyages = not List.isEmpty(voyages),
                            candidates = findCandidates(spec, voyages)
                        })
                }
        }

    ///
    /// 候補の選択キー。
    ///
    /// **航海番号を連結した文字列**である（`V-0001S` / `V-0001S|V-0002S`）。
    /// 候補は永続化しないため、画面から戻ってきたキーで**候補を引き直して**
    /// 突き合わせる。
    ///
    /// **フォームから区間の中身を受け取らない**。受け取ると利用者が改竄でき、
    /// 実在しない航海の旅程を予約に紐付けられる。キーだけなら、
    /// 一致しなければ何も起きない。
    ///
    pub def candidateKeyOf(c: RouteCandidate): String =
        RoutingRouteFinder.voyageNumbersOf(c) |> List.join("|")

    ///
    /// キーに対応する候補を選ぶ。
    ///
    /// **今の航海で引き直した候補の中から探す**。画面を開いてから選ぶまでに
    /// 航海が更新されていれば、キーは一致しない——**古い候補を割り当てない**。
    ///
    pub def selectCandidate(bookingId: String, key: String):
            Option[(RouteRequest, RouteCandidate)]
            \ BookingRouteRequest + VoyageRepo + Clock =
        match findFor(bookingId) {
            case RouteCandidatesResult.Found(f) =>
                f#candidates
                    |> List.find(c -> candidateKeyOf(c) == key)
                    |> Option.map(c -> (f#request, c))
            case _ => None
        }

    ///
    /// 依頼を探索条件へ写す。
    ///
    /// **ここで形式検証を通す**。`bookingId` はパス変数として外から来るが、
    /// 出発地・仕向地・期限は DB から読んだ値である。それでも検証するのは、
    /// **DB の値が常に正しいとは限らない**ためである（IT6 で `voyage` テーブルが
    /// 設計にあって実物に無かったように、想定と実態はずれる）。
    ///
    /// **現在時刻はここで取る**。ドメイン層（`RouteFinder`）は効果を要求しない。
    /// 「今日より前に出発する便を候補にしない」という判断はドメインのルールだが、
    /// **今日がいつかを知るのはアプリケーション層の責務**である（集約の実装規約）。
    def specOf(req: RouteRequest): Result[RouteSpecError, RouteSpec] \ Clock =
        routeSpec(RoutingBookingRouteRequest.originOf(req),
                  RoutingBookingRouteRequest.destinationOf(req),
                  Clock.today(),
                  RoutingBookingRouteRequest.arrivalDeadlineOf(req),
                  RoutingBookingRouteRequest.requiredOf(req))
}
