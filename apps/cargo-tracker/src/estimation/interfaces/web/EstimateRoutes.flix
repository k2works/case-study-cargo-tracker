///
/// 見積のルートハンドラ（US01・IT8）。
///
/// ここでの責務は 3 つに限る。
///
/// 1. リクエストから入力を取り出す
/// 2. ユースケースを呼ぶ
/// 3. 結果を画面へ写す（`EstimateError` を**利用者の言葉**へ翻訳する）
///
/// 判断そのものはユースケースとドメインにある。
///
mod EstimateRoutes {
    use EstimateRepo.EstimateRepo
    use EstimationCreateEstimate.{CreateResult, command, create, listAll, findByEstimateId}
    use EstimationModel.{CargoKind, EstimateError}
    use EstimationRouteSearch.RouteSearch
    use EstimatePages.{EstimateFormView, emptyForm, formOf, withError}
    use SharedClock.Clock
    use SharedHttp.{Request, Response, formParam, pathParam, html, htmlWithStatus}
    use SharedHttpAuth.{redirect, withHeader}
    use SharedIdGen.IdGen
    use SharedLocationCatalog.LocationCatalog
    use SharedSession.Session

    /// 一覧を表示する（PRG の遷移先）
    pub def index(req: Request): Response \ Session + EstimateRepo + IO =
        let flash = SharedHttpFlash.fromRequest(req)
            |> Option.filter(f -> match f {
                case SharedHttpFlash.Flash.EstimateCreated(_) => true
                case _                                        => false
               });
        let notice = Option.map(SharedHttpFlash.message, flash);
        let page = html(EstimatePages.renderIndex(Session.current(), listAll(),
                                                  Session.csrfToken(), notice));
        match flash {
            case None    => page
            case Some(_) => withHeader(page, "Set-Cookie",
                                       SharedHttpFlash.clearCookie(SharedAppEnv.isSecureCookie()))
        }

    /// 作成フォームを表示する
    pub def newPage(_req: Request): Response \ Session + LocationCatalog =
        html(EstimatePages.renderNew(Session.current(), emptyForm(),
                                     LocationCatalog.all(), Session.csrfToken()))

    ///
    /// 見積を作る。
    ///
    /// 成功時は PRG で一覧へ戻す。**発行された見積番号を通知として持たせる**——
    /// 電話口の営業は、その番号を相手に伝える。
    ///
    pub def create(req: Request):
            Response \ Session + EstimateRepo + RouteSearch + IdGen + Clock
                       + LocationCatalog + IO =
        let view = viewOf(req);
        let EstimatePages.EstimateFormView.EstimateFormView(v) = view;
        let cmd = command(v#origin, v#destination, v#arrivalDeadline,
                          EstimationModel.cargoKindFromPersisted(v#cargoType),
                          parseWeightGrams(v#weight));
        match EstimationCreateEstimate.create(cmd) {
            case CreateResult.Created(estimate) =>
                withHeader(redirect("/estimates"), "Set-Cookie",
                    SharedHttpFlash.setCookie(
                        SharedHttpFlash.Flash.EstimateCreated(
                            EstimationModel.estimateIdOf(estimate)),
                        SharedAppEnv.isSecureCookie()))
            case CreateResult.Invalid(e) => invalidForm(view, errorOf(e))
        }

    ///
    /// 見積詳細。
    ///
    /// **見つからないときは 404** である。他人の見積という概念は無い
    /// （見積は営業担当者の画面であり、荷主に開かない）。
    ///
    pub def show(req: Request): Response \ Session + EstimateRepo =
        let estimateId = pathParam(req, "estimateId") |> Option.getWithDefault("");
        match findByEstimateId(estimateId) {
            case None => htmlWithStatus(404,
                EstimatePages.renderNotFound(Session.current(), Session.csrfToken()))
            case Some(estimate) =>
                html(EstimatePages.renderDetail(Session.current(), estimate,
                                                Session.csrfToken()))
        }

    ///
    /// 検証エラーはフォームへ戻す。
    ///
    /// **200 で返す**（PRG は成功時のみ）。302 で戻すと入力内容が消え、
    /// 電話口の営業は打ち直すことになる。
    ///
    def invalidForm(view: EstimateFormView, error: (String, String)):
            Response \ Session + LocationCatalog =
        htmlWithStatus(200,
            EstimatePages.renderNew(Session.current(), withError(view, error),
                                    LocationCatalog.all(), Session.csrfToken()))

    def viewOf(req: Request): EstimateFormView =
        formOf(formValue(req, "origin"), formValue(req, "destination"),
               formValue(req, "arrivalDeadline"), formValue(req, "cargoType"),
               formValue(req, "weight"), Nil)

    def formValue(req: Request, name: String): String =
        formParam(req, name) |> Option.getWithDefault("")

    ///
    /// 重量（kg の文字列）をグラムへ変換する。
    ///
    /// 解釈は `SharedDecimal.parseScaled` に委ねる（予約フォームと同じ）。
    /// **0 とみなさない**。0 とみなすと、打ち間違いが「重量 0 の見積」として
    /// ドメインの検証に回り、利用者には何を直せばよいか伝わらない。
    ///
    def parseWeightGrams(value: String): Option[Int64] =
        SharedDecimal.parseScaled(3, value)

    /// ドメインの失敗理由を、入力欄と利用者の言葉へ翻訳する
    def errorOf(e: EstimateError): (String, String) = match e {
        case EstimateError.OriginIsRequired            => ("origin", EstimationModel.errorLabel(e))
        case EstimateError.DestinationIsRequired       => ("destination", EstimationModel.errorLabel(e))
        // **両方の欄に出さない**。片方を直せば解消するため、
        // 目的地の欄にだけ出す（利用者は「変えるならこちら」を選べる）
        case EstimateError.OriginAndDestinationAreSame => ("destination", EstimationModel.errorLabel(e))
        case EstimateError.LocationCodeIsMalformed     => ("origin", EstimationModel.errorLabel(e))
        case EstimateError.ArrivalDeadlineIsRequired   => ("arrivalDeadline", EstimationModel.errorLabel(e))
        case EstimateError.ArrivalDeadlineIsMalformed  => ("arrivalDeadline", EstimationModel.errorLabel(e))
        case EstimateError.ArrivalDeadlineIsInPast     => ("arrivalDeadline", EstimationModel.errorLabel(e))
        case EstimateError.WeightIsNotPositive         => ("weight", EstimationModel.errorLabel(e))
        case EstimateError.CargoKindIsMalformed        => ("cargoType", EstimationModel.errorLabel(e))
    }
}
