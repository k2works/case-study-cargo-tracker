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
    use SharedLocationCatalog.LocationCatalog
    use SharedHttpAuth.{redirect, withHeader}
    use SharedSession.Session

    /// 荷役を登録できるロールか。**判定は画面が持つ**（`canRegisterRole`）——
    /// 条件を 2 箇所に書くと、押せるのに必ず失敗するボタンができる（M9）
    def canRegister(): Bool \ Session =
        Session.current()
            |> Option.map(p -> HandlingPages.canRegisterRole(SharedSecurity.role(p)))
            |> Option.getWithDefault(false)

    ///
    /// 荷役履歴の一覧。
    ///
    /// 直前の登録結果を通知として出す。**出したら消す**。
    ///
    pub def index(req: Request): Response \ Session + HandlingRepo + IO =
        let flash = SharedHttpFlash.fromRequest(req)
            |> Option.filter(f -> match f {
                case SharedHttpFlash.Flash.HandlingRegistered(_)   => true
                case SharedHttpFlash.Flash.HandlingNotReflected(_) => true
                case _                                            => false
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
    pub def newPage(_req: Request): Response \ Session + LocationCatalog =
        html(HandlingPages.renderNew(Session.current(), emptyForm(),
                                     LocationCatalog.all(), Session.csrfToken(), None))

    ///
    /// 荷役を登録する。
    ///
    /// **拒まれたら入力を保ったまま再描画する**。追跡番号と日時を打ち直させない——
    /// 現場は片手で入力している。
    ///
    pub def create(req: Request): Response \ Session + HandlingRepo + CargoSnapshotSource
                                            + Clock + TrackingTransportStatusUpdate
                                            + BookingMisroutingReport + LocationCatalog + IO =
        let view = formOf(req);
        let typeText = param(req, "handlingType");
        // **マスタに無い港を集約の手前で止める**（IT11・M17）。集約は形式
        // （5 文字・英数字）しか見ない——実在するかはマスタが知っていることであり、
        // 通すと外部キー違反で 500 になる（IT4 で予約登録に入れた防御と同じ形）
        if (not isKnownLocation(param(req, "location")))
            invalid(view, "取り扱っていない場所です。一覧から作業した港を選んでください")
        else
        match HandlingModel.typeFromPersisted(typeText) {
            // 選択肢から選ぶため通常は起きない。**既定値へ倒さない**——
            // 「受領」として記録されると、積込の記録が消える
            case None => invalid(view, "作業種別を選択してください")
            case Some(handlingType) =>
                match HandlingRegisterHandling.register(
                        param(req, "trackingNumber"), handlingType,
                        param(req, "location"), param(req, "completionTime"),
                        Some(param(req, "voyageNumber")), currentOperator(),
                        Some(param(req, "consigneeConfirmation"))) {
                    case HandlingRegisterHandling.RegisterResult.Rejected(e) =>
                        invalid(view, messageOf(e))
                    case HandlingRegisterHandling.RegisterResult.CustomsNotCleared =>
                        // **なぜ**と**次に何を**の両方を出す（ADR-0014 決定 1）。
                        // 拒否だけでは現場が止まる
                        invalid(view,
                            "通関が完了していません。通関書類を受け取ったら" +
                            "作業種別「通関」で通関を記録してから、引取を登録してください")
                    case HandlingRegisterHandling.RegisterResult.CargoNotFound =>
                        invalid(view,
                            "その追跡番号は登録されていません。番号をご確認ください。" +
                            "追跡番号は経路設計者が発行します")
                    case HandlingRegisterHandling.RegisterResult.Registered(v, r) =>
                        // **反映の失敗を優先して出す**（IT11・M6）。判定の注意文より
                        // 「荷主の画面が更新されていない」ほうが緊急である
                        withHeader(redirect("/handling"), "Set-Cookie",
                            SharedHttpFlash.setCookie(registeredFlash(v, r),
                                SharedAppEnv.isSecureCookie()))
                }
        }

    ///
    /// 登録後に出す通知を選ぶ（IT11・M6）。
    ///
    /// 反映に失敗していれば**そちらを出す**。判定の注意文（要確認・経路逸脱）は
    /// 履歴の行にも出るが、反映の失敗はここでしか伝わらない。
    ///
    def registeredFlash(validity: HandlingValidity,
                        outcome: HandlingRegisterHandling.ReflectionOutcome):
            SharedHttpFlash.Flash =
        match outcome {
            case HandlingRegisterHandling.ReflectionOutcome.TrackingNotReflected =>
                SharedHttpFlash.Flash.HandlingNotReflected("TRACKING")
            case HandlingRegisterHandling.ReflectionOutcome.MisroutingNotReported =>
                SharedHttpFlash.Flash.HandlingNotReflected("MISROUTING")
            case HandlingRegisterHandling.ReflectionOutcome.Reflected =>
                SharedHttpFlash.Flash.HandlingRegistered(
                    HandlingModel.validityToPersisted(validity))
        }

    ///
    /// 記録した担当者（IT11・M10）。
    ///
    /// **入力させない**。ログイン利用者がそのまま担当者である——
    /// 入力欄にすると他人の名前を書ける。
    ///
    def currentOperator(): String \ Session =
        Session.current()
            |> Option.map(SharedSecurity.username)
            |> Option.getWithDefault("")

    def param(req: Request, name: String): String =
        formParam(req, name) |> Option.getWithDefault("")

    def formOf(req: Request): HandlingForm =
        handlingForm(param(req, "trackingNumber"), param(req, "handlingType"),
                     param(req, "location"), param(req, "completionTime"),
                     param(req, "voyageNumber"), param(req, "consigneeConfirmation"))

    ///
    /// マスタにある港か（IT11・M17）。
    ///
    /// **空は通す**。未入力は集約が「作業場所を入力してください」と言う——
    /// ここで拒むと「取り扱っていない場所です」になり、次の一手が変わってしまう。
    ///
    def isKnownLocation(code: String): Bool \ LocationCatalog =
        String.trim(code) == "" or
        LocationCatalog.all() |> List.exists(match (known, _) ->
            known == String.toUpperCase(String.trim(code)))

    def invalid(view: HandlingForm, message: String): Response \ Session + LocationCatalog =
        SharedHttp.htmlWithStatus(200,
            HandlingPages.renderNew(Session.current(), view,
                                    LocationCatalog.all(), Session.csrfToken(),
                                    Some(message)))

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
        case HandlingError.ConsigneeConfirmationIsRequired =>
            "荷受人の確認コードを入力してください。引取は所有権の移転であり、" +
            "確認の記録が要ります"
        case HandlingError.ConsigneeConfirmationIsNotApplicable =>
            "引取以外では荷受人の確認コードを入力しないでください"
    }
}
