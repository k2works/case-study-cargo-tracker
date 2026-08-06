///
/// 航海スケジュールのルートハンドラ（US24・US25・US07）。
///
/// ここでの責務は 3 つに限る。
///
/// 1. リクエストから入力を取り出す
/// 2. ユースケースを呼ぶ
/// 3. 結果を画面へ写す（`VoyageError` を**利用者の言葉**へ翻訳する）
///
/// 判断そのものはユースケースとドメインにある。ここに業務ルールを書かない。
///
mod VoyageRoutes {
    use RoutingModel.{CargoCapability, CarrierMovement, Voyage, VoyageError}
    use RoutingRegisterVoyage.{RegisterResult, UpdateResult, register, update, versionOf,
                               listAll, findByVoyageNumber, search}
    use SharedHttp.{Request, Response, formParam, formParams, queryParam, pathParam, html}
    use SharedHttpAuth.{redirect, withHeader}
    use SharedLocationCatalog.LocationCatalog
    use SharedSecurity.Role
    use SharedSession.Session
    use VoyagePages.{VoyageFormView, emptyForm, formOf, formOfVoyage, withError, withVersion}
    use VoyageRepo.VoyageRepo

    // ---- 一覧・検索（US07） ----

    ///
    /// 航路一覧（検索結果を兼ねる）。
    ///
    /// 検索条件を URL のクエリで受け取り、画面へ書き戻す。
    /// **0 件でも条件を保持する**（条件を緩和して再検索できる。受入基準 4）。
    ///
    pub def index(req: Request): Response \ Session + VoyageRepo + LocationCatalog + IO =
        let criteria = criteriaOf(req);
        let (origin, destination, departureFrom, departureTo, capabilityText) = criteria;
        let voyages = search(origin, destination, departureFrom, departureTo,
                             RoutingModel.capabilityFromPersisted(capabilityText));
        let flash = SharedHttpFlash.fromRequest(req)
            |> Option.filter(f -> match f {
                case SharedHttpFlash.Flash.VoyageRegistered(_) => true
                case _                                         => false
               });
        let notice = Option.map(SharedHttpFlash.message, flash);
        let page = html(VoyagePages.renderIndex(Session.current(), voyages, criteria,
                                                LocationCatalog.all(), Session.csrfToken(),
                                                notice, canManage()));
        match flash {
            case None    => page
            case Some(_) => withHeader(page, "Set-Cookie",
                                       SharedHttpFlash.clearCookie(SharedAppEnv.isSecureCookie()))
        }

    ///
    /// 登録・編集の操作ができるか。
    ///
    /// **検索は営業も使う**（見積の裏付けに要る）が、登録・更新は
    /// 経路設計者の職掌である（`ui_design.md` のロール表）。
    /// 画面はこの判定でボタンの有無を決める。**押せるのに 403 になる
    /// ボタンを出さない**（観点表の軸 3-7）。
    ///
    def canManage(): Bool \ Session =
        match Session.current() {
            case None    => false
            case Some(p) => SharedSecurity.role(p) == Role.Router
        }

    ///
    /// 検索条件を取り出す。
    ///
    /// **日付は形式を検査してから渡す**。検索の SQL は
    /// `CAST(? AS TIMESTAMP)` で受けるため、`?departureFrom=abc` のような値が
    /// そのまま届くと DB が例外を投げ、**利用者には 500 ですらなく応答なしが返る**
    /// （IT4 で `2026-02-30` によって起きた障害と同型。IT6 レビュー H1）。
    ///
    /// `<input type="date">` はブラウザ経由の入力しか守らない。**クエリ文字列は
    /// 誰でも書ける**。
    ///
    /// 形式が違う値は**絞り込みに使わない**（空文字＝指定なしへ落とす）。
    /// 検索は探す操作であり、打ち間違いで画面ごと落とすより、
    /// 条件を無視して結果を見せたうえで入力欄を空にするほうが次の行動へつながる。
    ///
    def criteriaOf(req: Request): (String, String, String, String, String) =
        (queryValue(req, "origin"), queryValue(req, "destination"),
         dateValue(req, "departureFrom"), dateValue(req, "departureTo"),
         queryValue(req, "capability"))

    def queryValue(req: Request, name: String): String =
        queryParam(req, name) |> Option.getWithDefault("")

    /// 日付として通る形だけを返す（それ以外は指定なし扱い）
    def dateValue(req: Request, name: String): String =
        let raw = queryValue(req, name);
        if (SharedCalendarText.isIsoDate(raw)) raw else ""

    // ---- 登録（US24） ----

    /// 登録フォームを表示する
    pub def newPage(_req: Request): Response \ Session + LocationCatalog =
        html(VoyagePages.renderNew(Session.current(), emptyForm(),
                                   LocationCatalog.all(), Session.csrfToken()))

    ///
    /// 区間 1 つ分の入力欄（htmx）。
    ///
    /// **フォーム全体を再描画しない**ため、入力途中の他の項目は失われない。
    /// 番号はクエリで受け取る（画面側が現在の区間数を知っている）。
    ///
    pub def legFields(req: Request): Response \ LocationCatalog =
        let index = queryParam(req, "index")
            |> Option.flatMap(Int32.fromString)
            |> Option.getWithDefault(1);
        html(VoyagePages.renderLegFields(index, LocationCatalog.all()))

    ///
    /// 航海スケジュールを登録する。
    ///
    /// 成功時は PRG で一覧へ戻す。**発行された航海番号を通知として持たせる**
    /// （US24 受入基準 5。登録できたことと、どの番号かを利用者が確認できる）。
    ///
    pub def create(req: Request):
            Response \ Session + VoyageRepo + LocationCatalog + IO =
        let view = viewOf(req, "");
        let VoyagePages.VoyageFormView.VoyageFormView(v) = view;
        match register(v#voyageNumber, v#vesselName, v#carrierName,
                       movementsOf(view), capabilitiesOf(view)) {
            case RegisterResult.Registered(voyage) =>
                withHeader(redirect("/voyages"), "Set-Cookie",
                    SharedHttpFlash.setCookie(
                        SharedHttpFlash.Flash.VoyageRegistered(
                            RoutingModel.voyageNumberOf(voyage)),
                        SharedAppEnv.isSecureCookie()))
            case RegisterResult.Duplicate(existing) =>
                // **重複を 500 にしない**。利用者が取れる行動（別の番号にする・
                // 既存を編集する）を示す
                invalidForm(view, ("voyageNumber",
                    "航海番号 ${RoutingModel.voyageNumberOf(existing)} は既に登録されています。" +
                    "別の番号にするか、一覧から編集してください"), false)
            case RegisterResult.Invalid(e) => invalidForm(view, errorOf(e), false)
        }

    // ---- 更新（US25） ----

    ///
    /// 編集フォームを表示する。
    ///
    /// **この時点の内容から版を作って持ち回る**。保存の直前に照合し、
    /// 差分確認の間に別の利用者が更新していたら検出する。
    ///
    pub def editPage(req: Request):
            Response \ Session + VoyageRepo + LocationCatalog =
        let voyageNumber = pathValue(req, "voyageNumber");
        match findByVoyageNumber(voyageNumber) {
            case None => notFound(voyageNumber)
            case Some(voyage) =>
                html(VoyagePages.renderEdit(Session.current(),
                                            formOfVoyage(voyage, versionOf(voyage)),
                                            LocationCatalog.all(), Session.csrfToken()))
        }

    ///
    /// 差分を確認する（US25 受入基準 2）。
    ///
    /// **保存はしない**。ここで見せた内容をそのまま持ち回り、
    /// 「更新する」で初めて保存する。確認画面で入力を作り直すと、
    /// 表示した内容と保存する内容がずれる余地が生まれる。
    ///
    pub def confirm(req: Request):
            Response \ Session + VoyageRepo + LocationCatalog =
        let voyageNumber = pathValue(req, "voyageNumber");
        let view = viewOf(req, formValue(req, "version"));
        match findByVoyageNumber(voyageNumber) {
            case None => notFound(voyageNumber)
            case Some(current) =>
                // 入力そのものが成立しないなら、差分を出す前に弾く
                match RoutingModel.registerVoyage(voyageNumber,
                        vesselNameOf(view), carrierNameOf(view),
                        movementsOf(view), capabilitiesOf(view)) {
                    case Err(e) => invalidForm(view, errorOf(e), true)
                    case Ok(updated) =>
                        html(VoyagePages.renderDiff(Session.current(), voyageNumber,
                                                    diffRows(current, updated), view,
                                                    Session.csrfToken(), None))
                }
        }

    ///
    /// 差分を承認して更新する。
    ///
    /// 版が食い違えば `Stale` として**現在の内容で差分を出し直す**。
    /// 「更新できませんでした」だけでは、利用者は何が起きたか分からない。
    ///
    pub def apply(req: Request):
            Response \ Session + VoyageRepo + LocationCatalog + IO =
        let voyageNumber = pathValue(req, "voyageNumber");
        let view = viewOf(req, formValue(req, "version"));
        match update(voyageNumber, formValue(req, "version"),
                     vesselNameOf(view), carrierNameOf(view),
                     movementsOf(view), capabilitiesOf(view)) {
            case UpdateResult.Updated(voyage) =>
                withHeader(redirect("/voyages"), "Set-Cookie",
                    SharedHttpFlash.setCookie(
                        SharedHttpFlash.Flash.VoyageRegistered(
                            RoutingModel.voyageNumberOf(voyage)),
                        SharedAppEnv.isSecureCookie()))
            case UpdateResult.NotFound => notFound(voyageNumber)
            case UpdateResult.Stale(current) =>
                // **現在の内容を見せてから、もう一度判断させる**（ADR-0008）。
                //
                // 版だけを最新へ差し替えて入力フォームへ戻すと、利用者が
                // そのまま「更新する」を押した瞬間に**相手の変更が黙って消える**
                // （IT6 レビュー H3）。楽観的ロックが 1 回の衝突で無効になる。
                //
                // 差分（現在 → 自分の入力）を出したうえで版を現在のものにする。
                // 見せてから押させるなら上書きは利用者の判断であり、
                // 見せずに押させるなら失われた更新である。
                match RoutingModel.registerVoyage(voyageNumber,
                        vesselNameOf(view), carrierNameOf(view),
                        movementsOf(view), capabilitiesOf(view)) {
                    case Err(e) => invalidForm(view, errorOf(e), true)
                    case Ok(mine) =>
                        html(VoyagePages.renderDiff(Session.current(), voyageNumber,
                            diffRows(current, mine), withVersion(view, versionOf(current)),
                            Session.csrfToken(),
                            Some("この航海は別の利用者によって更新されました。" +
                                 "下の「現在」は相手が保存した内容です。" +
                                 "上書きしてよければ「更新する」を押してください")))
                }
            case UpdateResult.Invalid(e) => invalidForm(view, errorOf(e), true)
        }

    ///
    /// 更新前後の差分を項目ごとに作る。
    ///
    /// **変わった項目だけを出す**。全項目を並べると、何が変わるのかを
    /// 利用者が読み取れない。
    ///
    def diffRows(before: Voyage, after: Voyage): List[(String, String, String)] =
        let rows =
            ("船名", RoutingModel.vesselNameOf(before), RoutingModel.vesselNameOf(after)) ::
            ("運送会社", RoutingModel.carrierNameOf(before), RoutingModel.carrierNameOf(after)) ::
            ("対応貨物種別", capabilityText(before), capabilityText(after)) ::
            ("航程", routeText(before), routeText(after)) :: Nil;
        rows |> List.filter(row -> {
            let (_, b, a) = row;
            b != a
        })

    def capabilityText(v: Voyage): String = RoutingModel.capabilitiesText(v)

    /// 航程を 1 行で表す（`JPOSA 11-01T10:00 → JPTYO 11-02T09:00 → ...`）
    def routeText(v: Voyage): String =
        RoutingModel.movementsOf(v)
            |> List.map(m ->
                "${RoutingModel.departureLocationOf(m)}(${RoutingModel.departureTimeOf(m)})" +
                "→${RoutingModel.arrivalLocationOf(m)}(${RoutingModel.arrivalTimeOf(m)})")
            |> List.join(" / ")

    // ---- 入力の取り出し ----

    ///
    /// リクエストの入力をフォームへ写す。
    ///
    /// 区間は**同名フィールドの並び**として届く。4 つの並びを添字で組にする。
    /// 数が揃わない場合は短い方に合わせる（欠けた区間は成立しない）。
    ///
    def viewOf(req: Request, version: String): VoyageFormView =
        let departures = formParams(req, "legDeparture");
        let arrivals = formParams(req, "legArrival");
        let departureTimes = formParams(req, "legDepartureTime");
        let arrivalTimes = formParams(req, "legArrivalTime");
        let legs = zip4(departures, arrivals, departureTimes, arrivalTimes);
        formOf(formValue(req, "voyageNumber"), formValue(req, "vesselName"),
               formValue(req, "carrierName"), formParams(req, "capabilities"),
               legs, version, Nil)

    /// 4 つの並びを組にする（短い方に合わせる）
    def zip4(a: List[String], b: List[String], c: List[String], d: List[String]):
            List[(String, String, String, String)] =
        match (a, b, c, d) {
            case (x :: xs, y :: ys, z :: zs, w :: ws) => (x, y, z, w) :: zip4(xs, ys, zs, ws)
            case _                                    => Nil
        }

    def formValue(req: Request, name: String): String =
        formParam(req, name) |> Option.getWithDefault("")

    def pathValue(req: Request, name: String): String =
        pathParam(req, name) |> Option.getWithDefault("")

    ///
    /// フォームの値を取り出す（IT8・TS14。IT6 レビュー L1）。
    ///
    /// IT6 は `fieldOf(view, "vesselName")` という**文字列キー**のアクセサ
    /// だった。打ち間違えてもコンパイルは通り、既定の `""` が返る——
    /// **船名が空欄として扱われ、検証エラーの文面だけが出る**。
    /// 原因はフォームの入力ではなくキーの綴りにある、と気付く手掛かりが無い。
    ///
    /// レコードのフィールドを直に読む関数に分ければ、綴りの誤りは
    /// **コンパイルで止まる**。
    ///
    def vesselNameOf(view: VoyageFormView): String =
        let VoyagePages.VoyageFormView.VoyageFormView(v) = view;
        v#vesselName

    def carrierNameOf(view: VoyageFormView): String =
        let VoyagePages.VoyageFormView.VoyageFormView(v) = view;
        v#carrierName

    def movementsOf(view: VoyageFormView): List[CarrierMovement] =
        let VoyagePages.VoyageFormView.VoyageFormView(v) = view;
        v#legs |> List.map(leg -> {
            let (departure, arrival, departureTime, arrivalTime) = leg;
            RoutingModel.movement(departure, arrival, departureTime, arrivalTime)
        })

    ///
    /// 選択された対応貨物種別を取り出す。
    ///
    /// **未知の値は落とす**。既定値へ倒すと、選ばれていない種別が
    /// 対応可能として登録される（危険物が典型例）。
    ///
    def capabilitiesOf(view: VoyageFormView): List[CargoCapability] =
        let VoyagePages.VoyageFormView.VoyageFormView(v) = view;
        v#capabilities |> List.filterMap(RoutingModel.capabilityFromPersisted)

    // ---- 結果を画面へ写す ----

    def invalidForm(view: VoyageFormView, error: (String, String), isEdit: Bool):
            Response \ Session + LocationCatalog =
        let withErr = withError(view, error);
        if (isEdit)
            html(VoyagePages.renderEdit(Session.current(), withErr,
                                        LocationCatalog.all(), Session.csrfToken()))
        else
            html(VoyagePages.renderNew(Session.current(), withErr,
                                       LocationCatalog.all(), Session.csrfToken()))

    def notFound(voyageNumber: String): Response \ Session =
        SharedHttp.htmlWithStatus(404,
            VoyagePages.renderNotFound(Session.current(), voyageNumber, Session.csrfToken()))

    ///
    /// ドメインの失敗理由を**利用者の言葉**へ翻訳する。
    ///
    /// 内部語（`ScheduleIsEmpty`）を画面に出さない。返すのは
    /// 「どのフィールドの」「何が問題か」の組である。
    ///
    def errorOf(e: VoyageError): (String, String) = match e {
        case VoyageError.VoyageNumberIsRequired =>
            ("voyageNumber", "航海番号を入力してください")
        case VoyageError.VoyageNumberIsTooLong =>
            ("voyageNumber", "航海番号は 20 文字以内で入力してください")
        case VoyageError.VoyageNumberIsMalformed =>
            ("voyageNumber",
             "航海番号は半角の大文字英数字とハイフンで入力してください（例: V-0042）")
        case VoyageError.VesselNameIsRequired =>
            ("vesselName", "船名を入力してください")
        case VoyageError.VesselNameIsTooLong =>
            ("vesselName", "船名は 100 文字以内で入力してください")
        case VoyageError.CarrierNameIsRequired =>
            ("carrierName", "運送会社を入力してください")
        case VoyageError.CarrierNameIsTooLong =>
            ("carrierName", "運送会社は 100 文字以内で入力してください")
        case VoyageError.ScheduleIsEmpty =>
            ("legs", "区間を 1 つ以上入力してください")
        case VoyageError.MovementEndpointsAreSame =>
            ("legs", "区間の出発港と到着港が同じです。別の港を選択してください")
        case VoyageError.LocationCodeIsMalformed =>
            ("legs", "出発港・到着港を一覧から選択してください")
        case VoyageError.MovementTimeIsMalformed =>
            ("legs", "出発日時・到着日時を入力してください")
        case VoyageError.MovementTimesAreInverted =>
            ("legs", "出発日時は到着日時以前で入力してください")
        case VoyageError.MovementsAreDisconnected =>
            ("legs", "区間がつながっていません。前の区間の到着港と次の区間の出発港を合わせてください")
        case VoyageError.MovementsAreOutOfOrder =>
            ("legs", "区間の順序が時系列になっていません。前の区間の到着日時以降に次の区間を出発させてください")
        case VoyageError.CapabilitiesAreEmpty =>
            ("capabilities", "対応貨物種別を 1 つ以上選択してください")
    }
}
