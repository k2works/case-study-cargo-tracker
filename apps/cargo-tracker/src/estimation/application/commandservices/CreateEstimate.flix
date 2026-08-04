///
/// 見積の作成（US01・IT8）。
///
/// アプリケーション層の責務は 3 つに限る。
///
/// 1. 効果（識別子・時刻・経路探索・永続化）を集める
/// 2. ドメインの判断を呼ぶ
/// 3. 結果を返す
///
/// **判断そのものはドメインにある**。ここに業務ルールを書かない。
///
mod EstimationCreateEstimate {
    use EstimateRepo.EstimateRepo
    use EstimationModel.{Estimate, CargoKind, EstimateError, RouteCandidate}
    use EstimationRouteSearch.{RouteSearch, FoundRoute}
    use SharedClock.Clock
    use SharedIdGen.IdGen

    /// 見積の作成結果
    pub enum CreateResult {
        case Created(Estimate)
        case Invalid(EstimateError)
    }

    ///
    /// 入力途中の値をまとめた指示。
    ///
    /// **重量はグラムの整数で受け取る**。文字列の解釈は境界（interfaces 層）の
    /// 責務であり、アプリケーション層は数として成立するものを受け取る。
    ///
    pub enum CreateCommand({
        origin = String,
        destination = String,
        arrivalDeadline = String,
        cargoKind = Option[CargoKind],
        weightGrams = Option[Int64]
    })

    pub def command(origin: String, destination: String, arrivalDeadline: String,
                    cargoKind: Option[CargoKind], weightGrams: Option[Int64]): CreateCommand =
        CreateCommand.CreateCommand({
            origin = origin, destination = destination, arrivalDeadline = arrivalDeadline,
            cargoKind = cargoKind, weightGrams = weightGrams
        })

    ///
    /// 見積を作る。
    ///
    /// **候補が 0 件でも見積は保存する**。「その期限では無理です」と答えたことも
    /// 記録である。保存しないと、同じ引き合いが再び来たときに
    /// 「前回どう答えたか」が分からない（US01 の利用場面 3）。
    ///
    pub def create(cmd: CreateCommand):
            CreateResult \ EstimateRepo + RouteSearch + IdGen + Clock =
        let CreateCommand.CreateCommand(c) = cmd;
        match (c#cargoKind, c#weightGrams) {
            // **形式の誤りをドメインへ持ち込まない**。ドメインは
            // 「数として成立するもの」を前提にしてよい
            case (None, _) => CreateResult.Invalid(EstimateError.CargoKindIsMalformed)
            case (_, None) => CreateResult.Invalid(EstimateError.WeightIsNotPositive)
            case (Some(kind), Some(weightGrams)) =>
                // **採番した時点で `EST-` の形にする**（予約の `BK-` と同じ）。
                // UUID を識別子にすると URL に UUID が現れ、利用者が
                // 電話口で読み上げる番号と画面の URL が別物になる
                let estimateId = EstimationModel.generateEstimateId(IdGen.newId());
                match EstimationModel.createEstimate(estimateId, c#origin, c#destination,
                                                    c#arrivalDeadline, kind, weightGrams,
                                                    Clock.today()) {
                    case Err(e) => CreateResult.Invalid(e)
                    case Ok(estimate) => {
                        let withCandidates =
                            EstimationModel.replaceCandidates(estimate,
                                candidatesFor(estimate));
                        EstimateRepo.save(withCandidates);
                        CreateResult.Created(withCandidates)
                    }
                }
        }

    ///
    /// 経路候補を求め、運賃を付ける。
    ///
    /// **探索は ACL に、運賃は Estimation に**（[ADR-0010](../../../../../../docs/adr/ADR-0010-estimation-reuses-route-finder-via-composition.md)）。
    /// 探索結果に金額が含まれていたら、運賃の改定が Routing の変更に見えてしまう。
    ///
    def candidatesFor(estimate: Estimate): List[RouteCandidate] \ RouteSearch =
        let kind = EstimationModel.cargoKindOf(estimate);
        RouteSearch.search(EstimationModel.originOf(estimate),
                           EstimationModel.destinationOf(estimate),
                           EstimationModel.arrivalDeadlineOf(estimate),
                           EstimationModel.cargoKindToPersisted(kind))
            |> List.map(found -> {
                let cost = EstimationFreightRate.estimate(kind,
                    EstimationModel.weightGramsOf(estimate),
                    EstimationRouteSearch.routeLegCountOf(found));
                EstimationModel.routeCandidate(
                    EstimationRouteSearch.routeVoyageNumbersOf(found),
                    EstimationRouteSearch.routeTransitPortsOf(found),
                    EstimationRouteSearch.routeTransitDaysOf(found),
                    cost)
            })

    /// 見積を一覧する（新しい順。並びはリポジトリが決める）
    pub def listAll(): List[Estimate] \ EstimateRepo =
        EstimateRepo.findAll()

    /// 見積番号の元になった識別子で 1 件引く
    pub def findByEstimateId(estimateId: String): Option[Estimate] \ EstimateRepo =
        EstimateRepo.findByEstimateId(estimateId)
}
