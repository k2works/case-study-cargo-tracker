///
/// 経路候補の算出（US08）。
///
/// **効果を一切要求しない純粋関数**である（ドメインサービス）。
/// 航海の一覧を渡す責務はアプリケーション層にあり、ここは探索だけを行う。
///
/// # 到達可能性の判定に Datalog を使う
///
/// [バックエンドアーキテクチャ](../../../../../../docs/design/architecture_backend.md)
/// 「Routing Context における Datalog の活用」に従い、港の到達可能性を
/// 推移閉包で導出する。**Datalog は本モジュールの内部に閉じる**。
/// 公開する型（`RouteCandidate` / `RouteLeg`）に Datalog の型は現れず、
/// 他コンテキストはもちろん本 BC の他の層も Datalog を意識しない。
///
/// # Datalog だけでは足りない理由
///
/// 推移閉包が答えるのは「港がつながっているか」だけである。実際に乗れるかは
/// **時刻**（前の船が着く前に次の船が出ていないか）と**対応貨物種別**に依存し、
/// これらは推移閉包の外にある。したがって
///
/// 1. Datalog で「そもそも到達しうるか」を判定し、探索を刈る
/// 2. 刈った空間を深さ優先で辿り、時刻・種別・重複を検証する
///
/// の 2 段構えとする。1 を省くと、到達不能な仕向地でも全組み合わせを
/// 展開してから捨てることになる。
///
/// # 探索の上限
///
/// **積み替えは 2 回（区間 3 本）まで**。推移閉包は経路数が指数的に増える。
/// 業務上も 3 回以上の積み替えは実運用で採らない。
///
mod RoutingRouteFinder {
    use RoutingModel.{CargoCapability, Voyage, CarrierMovement}
    use RoutingModel.{voyageNumberOf, vesselNameOf, capabilitiesOf, movementsOf}
    use RoutingModel.{departureLocationOf, arrivalLocationOf}
    use RoutingRouteSpec.RouteSpec
    use RoutingRouteSpec.{originOf, destinationOf, arrivalDeadlineOf, requiredOf}

    /// 積み替えの上限（回数）。区間数はこれに 1 を足した値になる
    pub def maxTransshipments(): Int32 = 2

    ///
    /// 経路の 1 区間。
    ///
    /// **`Booking` の `Leg` と分ける**。あちらは確定した旅程の構成要素であり、
    /// こちらは候補の構成要素である。確定していないものに確定後の型を使うと、
    /// 「まだ選ばれていない」という状態が型から読めなくなる。
    ///
    pub enum RouteLeg({
        voyageNumber = String,
        vesselName = String,
        loadLocation = String,
        unloadLocation = String,
        loadTime = String,
        unloadTime = String
    })

    ///
    /// 経路候補。
    ///
    /// **費用を持たない**。運賃モデルは US01（見積・IT8）で定義される。
    /// 値の無い列を出すと、それは嘘の数値になる。
    ///
    pub enum RouteCandidate({
        legs = List[RouteLeg]
    })

    // ---- 探索 ----

    ///
    /// 条件を満たす経路候補を推奨順で返す。
    ///
    /// 推奨順は **積み替え回数の少ない順 → 到着日時の早い順 → 航海番号の昇順**。
    /// 直行便を最優先にするのは受入基準 5 であり、到着順を上に置くと
    /// 「より早く着く積み替え便」に直行便が沈む。
    /// 第 3 のキーは並びを決定的にするためである（テストが環境で揺れない）。
    ///
    pub def findCandidates(spec: RouteSpec, voyages: List[Voyage]): List[RouteCandidate] =
        let usable = voyages |> List.filter(v -> RoutingModel.supports(v, requiredOf(spec)));
        let hops = usable |> List.flatMap(hopsOf);
        if (not isReachable(originOf(spec), destinationOf(spec), hops))
            Nil
        else
            search(spec, hops, originOf(spec), None, Nil, Nil)
                |> List.map(legs -> RouteCandidate.RouteCandidate({legs = legs}))
                |> List.sortBy(orderKey)

    ///
    /// 港の到達可能性（Datalog の推移閉包）。
    ///
    /// **時刻も種別も見ない**。ここで見るのは「線がつながっているか」だけであり、
    /// 真であることは経路が存在する**必要条件**にすぎない。
    /// 十分条件の検証は `search` が行う。
    ///
    def isReachable(origin: String, destination: String, hops: List[Hop]): Bool =
        let facts = hops |> List.foldLeft((acc, h) ->
            acc <+> #{ Edge(fromOf(h), toOf(h)). }, #{});
        let rules = #{
            Reachable(x, y) :- Edge(x, y).
            Reachable(x, z) :- Reachable(x, y), Edge(y, z).
        };
        let db = facts <+> rules;
        let reached = query db select y from Reachable(origin, y);
        Vector.exists(y -> y == destination, reached)

    ///
    /// 深さ優先で経路を組み立てる。
    ///
    /// - `at`: 現在地
    /// - `arrivedAt`: 現在地への到着時刻（出発地では `None`）
    /// - `used`: 使用済みの航海番号（同じ航海に 2 度乗らない）
    /// - `acc`: ここまでの区間（逆順）
    ///
    /// **`used` で航海を除外する**のは循環を止めるためである。港で除外すると
    /// 「同じ港へ戻ってから別の船で出る」正当な経路まで消える。
    ///
    def search(spec: RouteSpec, hops: List[Hop], at: String, arrivedAt: Option[String],
               used: List[String], acc: List[RouteLeg]): List[List[RouteLeg]] =
        if (List.length(acc) > maxTransshipments())
            Nil
        else
            hops
                |> List.filter(h -> fromOf(h) == at)
                |> List.filter(h -> not List.memberOf(voyageOf(h), used))
                |> List.filter(h -> connects(arrivedAt, departsAtOf(h)))
                |> List.flatMap(h -> {
                    let legs = legOf(h) :: acc;
                    let here =
                        if (toOf(h) == destinationOf(spec) and arrivesBy(spec, arrivesAtOf(h)))
                            List.reverse(legs) :: Nil
                        else
                            Nil;
                    let deeper =
                        if (toOf(h) == destinationOf(spec))
                            // 仕向地に着いたらそこで終える。通過して戻る経路は業務上採らない
                            Nil
                        else
                            search(spec, hops, toOf(h), Some(arrivesAtOf(h)),
                                   voyageOf(h) :: used, legs);
                    here ::: deeper
                })

    ///
    /// 接続が成立するか（前の到着 ≤ 次の出発）。
    ///
    /// **同時刻を成立とする**。`Voyage` のビジネスルール 6（出発時刻は到着時刻
    /// 以前。同一時刻を許す）と揃える。最低接続時間は業務ルールとして
    /// 未定義であり、実装が勝手に決めない。
    ///
    /// 時刻は `YYYY-MM-DDTHH:MM` の固定長であり、辞書順が時系列順に一致する。
    ///
    def connects(arrivedAt: Option[String], departsAt: String): Bool = match arrivedAt {
        case None    => true
        case Some(a) => a <= departsAt
    }

    ///
    /// 到着期限に間に合うか。
    ///
    /// **日付単位で比べる**。期限は `DATE`（`2026-04-15`）、到着は
    /// `TIMESTAMP`（`2026-04-15T18:00`）であり、素朴に比較すると
    /// 期限当日に着く便がすべて刈られる。IT4 と IT6 で 2 度、
    /// 型の違う値を素朴に比べる誤りが起きている。
    ///
    def arrivesBy(spec: RouteSpec, arrivalTime: String): Bool =
        SharedCalendarText.dateOf(arrivalTime) <= arrivalDeadlineOf(spec)

    // ---- 区間の展開 ----

    ///
    /// 航海 1 本から「乗れる区間」をすべて取り出す。
    ///
    /// 航海の `movements` は連結・時系列順であることが保証されている。
    /// 貨物は **i 番目の出発地で積み、j 番目（i ≤ j）の到着地で降ろせる**。
    /// この展開により、**寄港地からの積み込み・寄港地での荷降ろし**が
    /// 探索の対象に入る（IT6 レビュー M1・US07 受入基準 3）。
    ///
    def hopsOf(v: Voyage): List[Hop] =
        let ms = movementsOf(v);
        let n = List.length(ms);
        List.range(0, n) |> List.flatMap(i ->
            List.range(i, n) |> List.filterMap(j ->
                match (List.nth(i, ms), List.nth(j, ms)) {
                    case (Some(start), Some(finish)) =>
                        Some(Hop.Hop({
                            voyageNumber = voyageNumberOf(v),
                            vesselName = vesselNameOf(v),
                            fromPort = departureLocationOf(start),
                            toPort = arrivalLocationOf(finish),
                            departsAt = RoutingModel.departureTimeOf(start),
                            arrivesAt = RoutingModel.arrivalTimeOf(finish)
                        }))
                    case _ => None
                }))

    /// 乗れる区間（航海の一部または全部）
    enum Hop({
        voyageNumber = String,
        vesselName = String,
        fromPort = String,
        toPort = String,
        departsAt = String,
        arrivesAt = String
    })

    def voyageOf(h: Hop): String = let Hop.Hop(r) = h; r#voyageNumber
    def fromOf(h: Hop): String = let Hop.Hop(r) = h; r#fromPort
    def toOf(h: Hop): String = let Hop.Hop(r) = h; r#toPort
    def departsAtOf(h: Hop): String = let Hop.Hop(r) = h; r#departsAt
    def arrivesAtOf(h: Hop): String = let Hop.Hop(r) = h; r#arrivesAt

    def legOf(h: Hop): RouteLeg =
        let Hop.Hop(r) = h;
        RouteLeg.RouteLeg({
            voyageNumber = r#voyageNumber, vesselName = r#vesselName,
            loadLocation = r#fromPort, unloadLocation = r#toPort,
            loadTime = r#departsAt, unloadTime = r#arrivesAt
        })

    // ---- 整列 ----

    /// 推奨順の整列キー（積み替え回数 → 到着日時 → 航海番号の並び）
    def orderKey(c: RouteCandidate): (Int32, String, String) =
        (transshipmentCountOf(c), arrivalTimeOf(c),
         voyageNumbersOf(c) |> List.join("-"))

    // ---- 候補の参照 ----

    /// 区間の一覧
    pub def legsOf(c: RouteCandidate): List[RouteLeg] =
        let RouteCandidate.RouteCandidate(r) = c;
        r#legs

    /// 通過する航海番号（順序どおり）
    pub def voyageNumbersOf(c: RouteCandidate): List[String] =
        legsOf(c) |> List.map(legVoyageNumberOf)

    /// 積み替え回数（区間数 - 1）
    pub def transshipmentCountOf(c: RouteCandidate): Int32 =
        Int32.max(0, List.length(legsOf(c)) - 1)

    /// 直行便か
    pub def isDirect(c: RouteCandidate): Bool =
        transshipmentCountOf(c) == 0

    /// 出航時刻（先頭区間の積込時刻）
    pub def departureTimeOf(c: RouteCandidate): String =
        legsOf(c) |> List.head |> Option.map(legLoadTimeOf) |> Option.getWithDefault("")

    /// 最終到着時刻（末尾区間の荷降時刻）
    pub def arrivalTimeOf(c: RouteCandidate): String =
        legsOf(c) |> List.last |> Option.map(legUnloadTimeOf) |> Option.getWithDefault("")

    ///
    /// 経由港（積み替えを行う港）。
    ///
    /// 出発地と仕向地は含めない。**「どこで積み替えるか」が経路設計者の関心**であり、
    /// 端点は探索条件として既に分かっている。
    ///
    pub def transitPortsOf(c: RouteCandidate): List[String] =
        let legs = legsOf(c);
        match List.length(legs) {
            case 0 => Nil
            case n => legs |> List.take(n - 1) |> List.map(legUnloadLocationOf)
        }

    ///
    /// 所要日数（出航日から最終到着日まで）。
    ///
    /// **区間ごとの日数を足さない**。積み替えの待ち時間が落ちる。
    /// 利用者が知りたいのは「預けてから着くまで」である。
    ///
    pub def transitDaysOf(c: RouteCandidate): Int32 =
        SharedCalendarText.daysBetween(departureTimeOf(c), arrivalTimeOf(c))
            |> Option.getWithDefault(0)

    /// 期限当日に着くか（画面で注意を促す）
    pub def arrivesOnDeadline(spec: RouteSpec, c: RouteCandidate): Bool =
        SharedCalendarText.dateOf(arrivalTimeOf(c)) == arrivalDeadlineOf(spec)

    // ---- 区間の参照 ----

    pub def legVoyageNumberOf(l: RouteLeg): String = let RouteLeg.RouteLeg(r) = l; r#voyageNumber
    pub def legVesselNameOf(l: RouteLeg): String = let RouteLeg.RouteLeg(r) = l; r#vesselName
    pub def legLoadLocationOf(l: RouteLeg): String = let RouteLeg.RouteLeg(r) = l; r#loadLocation
    pub def legUnloadLocationOf(l: RouteLeg): String = let RouteLeg.RouteLeg(r) = l; r#unloadLocation
    pub def legLoadTimeOf(l: RouteLeg): String = let RouteLeg.RouteLeg(r) = l; r#loadTime
    pub def legUnloadTimeOf(l: RouteLeg): String = let RouteLeg.RouteLeg(r) = l; r#unloadTime
}
