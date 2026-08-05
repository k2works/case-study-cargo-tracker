///
/// 荷役作業のルート（US15。IT10）。
///
mod HandlingRoutes {
    use HandlingBookingMisroutingReport.BookingMisroutingReport
    use HandlingCargoSnapshotSource.CargoSnapshotSource
    use HandlingModel.{HandlingError, HandlingType, HandlingValidity}
    use HandlingPages.{HandlingForm, emptyForm, handlingForm}
    use HandlingRepo.HandlingRepo
    use HandlingTrackingTransportStatusUpdate.TrackingTransportStatusUpdate
    use SharedClock.Clock
    use SharedHttp.{Request, Response, formParam, html}
    use SharedHttpAuth.{redirect, withHeader}
    use SharedSession.Session

    /// 荷役を登録できるロールか（荷役作業員）
    def canRegister(): Bool \ Session =
        Session.current()
            |> Option.map(p -> SharedSecurity.role(p) == SharedSecurity.Role.Handler
                              or SharedSecurity.role(p) == SharedSecurity.Role.Admin)
            |> Option.getWithDefault(false)

    ///
    /// 荷役履歴の一覧。
    ///
    /// 直前の登録結果を通知として出す。**出したら消す**。
    ///
    pub def index(req: Request): Response \ Session + HandlingRepo + IO =
        let flash = SharedHttpFlash.fromRequest(req)
            |> Option.filter(f -> match f {
                case SharedHttpFlash.Flash.HandlingRegistered(_) => true
                case _                                          => false
               });
        let notice = Option.map(SharedHttpFlash.message, flash);
        // 絞り込みは追跡番号 1 本に絞る（IT11・H9）。判定・期間も候補に挙がったが、
        // 追跡管理者が実際に手にしているのは問い合わせで告げられた番号である
        let filter = SharedHttp.queryParam(req, "trackingNumber")
            |> Option.getWithDefault("");
        let found = HandlingHistory.search(Some(filter));
        let page = html(HandlingPages.renderIndex(Session.current(),
                                                  fst(found), snd(found), filter,
                                                  Session.csrfToken(), notice,
                                                  canRegister()));
        match flash {
            case None    => page
            case Some(_) => withHeader(page, "Set-Cookie",
                                       SharedHttpFlash.clearCookie(SharedAppEnv.isSecureCookie()))
        }

    /// 登録フォーム
    pub def newPage(_req: Request): Response \ Session =
        html(HandlingPages.renderNew(Session.current(), emptyForm(),
                                     Session.csrfToken(), None))

    ///
    /// 荷役を登録する。
    ///
    /// **拒まれたら入力を保ったまま再描画する**。追跡番号と日時を打ち直させない——
    /// 現場は片手で入力している。
    ///
    pub def create(req: Request): Response \ Session + HandlingRepo + CargoSnapshotSource
                                            + Clock + TrackingTransportStatusUpdate
                                            + BookingMisroutingReport + IO =
        let view = formOf(req);
        let typeText = param(req, "handlingType");
        match HandlingModel.typeFromPersisted(typeText) {
            // 選択肢から選ぶため通常は起きない。**既定値へ倒さない**——
            // 「受領」として記録されると、積込の記録が消える
            case None => invalid(view, "作業種別を選択してください")
            case Some(handlingType) =>
                match HandlingRegisterHandling.register(
                        param(req, "trackingNumber"), handlingType,
                        param(req, "location"), param(req, "completionTime"),
                        Some(param(req, "voyageNumber"))) {
                    case HandlingRegisterHandling.RegisterResult.Rejected(e) =>
                        invalid(view, messageOf(e))
                    case HandlingRegisterHandling.RegisterResult.CargoNotFound =>
                        invalid(view,
                            "その追跡番号は登録されていません。番号をご確認ください。" +
                            "追跡番号は経路設計者が発行します")
                    case HandlingRegisterHandling.RegisterResult.Registered(v) =>
                        withHeader(redirect("/handling"), "Set-Cookie",
                            SharedHttpFlash.setCookie(
                                SharedHttpFlash.Flash.HandlingRegistered(
                                    HandlingModel.validityToPersisted(v)),
                                SharedAppEnv.isSecureCookie()))
                }
        }

    def param(req: Request, name: String): String =
        formParam(req, name) |> Option.getWithDefault("")

    def formOf(req: Request): HandlingForm =
        handlingForm(param(req, "trackingNumber"), param(req, "handlingType"),
                     param(req, "location"), param(req, "completionTime"),
                     param(req, "voyageNumber"))

    def invalid(view: HandlingForm, message: String): Response \ Session =
        SharedHttp.htmlWithStatus(200,
            HandlingPages.renderNew(Session.current(), view,
                                    Session.csrfToken(), Some(message)))

    ///
    /// 入力の誤りを画面の言葉へ。**次に何をすればよいかを言う**。
    ///
    def messageOf(e: HandlingError): String = match e {
        case HandlingError.TrackingNumberIsRequired =>
            "追跡番号を入力してください"
        case HandlingError.LocationIsRequired =>
            "作業場所を入力してください"
        case HandlingError.LocationCodeIsMalformed =>
            "作業場所は UN/LOCODE 5 文字で入力してください（例: JPOSA）"
        case HandlingError.CompletionTimeIsRequired =>
            "作業日時を入力してください"
        case HandlingError.CompletionTimeIsMalformed =>
            "作業日時に実在する日時を指定してください（例: 2026-08-05T10:00）"
        case HandlingError.CompletionTimeIsInFuture =>
            "作業日時に未来の日付は指定できません。実際に作業した日時を入力してください"
        case HandlingError.VoyageNumberIsRequired =>
            "積込・荷降しのときは航海番号が必要です。作業した便の番号を入力してください"
        case HandlingError.VoyageNumberIsNotApplicable =>
            "受領のときは航海番号を入力しないでください。受領は便を伴いません"
        case HandlingError.HandlingTypeIsMalformed =>
            "作業種別を選択してください"
    }
}
