///
/// 貨物予約のルートハンドラ（US04）。
///
mod BookingRoutes {
    use BookingBookCargo.{BookResult, book, command, listAll}
    use BookingModel.{CargoType, BookingError}
    use BookingPages.{BookingFormView, emptyForm, formOf, renderNew, renderIndex,
                      renderCargoTypeFields}
    use CargoRepo.CargoRepo
    use SharedHttp.{Request, Response, formParam, queryParam, pathParam, html}
    use SharedHttpAuth.{redirect, withHeader}
    use SharedIdGen.IdGen
    use SharedClock.Clock
    use SharedLocationCatalog.LocationCatalog
    use SharedSession.Session
    use ShipperExistenceChecker.ShipperExistenceChecker

    ///
    /// 登録フォームを表示する。
    ///
    /// 荷主一覧の「この荷主で予約する」から来た場合、`shipperCode` が
    /// クエリで届く。**着地先で選択済みにする**ことまでが導線であり、
    /// フォームを開くだけでは利用者は結局コードを転記することになる。
    ///
    pub def newPage(req: Request): Response \ Session + LocationCatalog =
        let shipperCode = queryParam(req, "shipperCode") |> Option.getWithDefault("");
        let view = formOf(shipperCode, "", "", "", "GENERAL", "", "", "", "", "", "",
                          "", "", "", "", "", "CELSIUS", Nil);
        html(renderNew(Session.current(), view, LocationCatalog.all(), Session.csrfToken()))

    /// 貨物種別に応じた追加入力欄（htmx）。US05 の拡張点
    pub def cargoTypeFields(req: Request): Response =
        let cargoType = queryParam(req, "cargoType") |> Option.getWithDefault("GENERAL");
        html(renderCargoTypeFields(formOf("", "", "", "", cargoType, "", "", "", "", "", "",
                                          "", "", "", "", "", "CELSIUS", Nil)))

    ///
    /// 貨物予約を一覧する。
    ///
    /// PRG の遷移先であり、直前の予約結果を通知として出す。**出したら消す**。
    ///
    pub def index(req: Request): Response \ Session + CargoRepo + ShipperExistenceChecker + IO =
        let flash = SharedHttpFlash.fromRequest(req)
            |> Option.filter(f -> match f {
                // **予約一覧に出すのは予約の通知だけ**。荷主登録の通知が
                // ここに出ると、利用者は何が起きたのか分からなくなる
                case SharedHttpFlash.Flash.CargoBooked(_) => true
                case _                                    => false
               });
        let notice = Option.map(SharedHttpFlash.message, flash);
        let viewer = Session.current();
        let keyword = queryParam(req, "q") |> Option.getWithDefault("");
        // **可視範囲と絞り込みを 1 箇所で解決する**。画面ごとに書くと片方だけ忘れる
        let cargos = BookingBookCargo.listVisible(viewer, keyword);
        let rows = cargos |> List.map(c -> (c, shipperOf(c)));
        let page = html(renderIndex(viewer, rows, Session.csrfToken(), notice,
                                    keyword, canBook()));
        match flash {
            case None    => page
            case Some(_) => withHeader(page, "Set-Cookie",
                                       SharedHttpFlash.clearCookie(SharedAppEnv.isSecureCookie()))
        }

    ///
    /// 予約の登録・引き渡しができるロールか。
    ///
    /// **押せるのに 403 になるボタンを出さない**（観点表の軸 3-7）。
    /// IT6 で荷主に一覧と詳細を開いたため、ロールでの出し分けが要る
    /// （IT6 レビュー H9）。ルート表では `/bookings/new` と
    /// `assign-to-routing` がいずれも `salesOnly()` である。
    ///
    def canBook(): Bool \ Session =
        match Session.current() {
            case None    => false
            case Some(p) => SharedSecurity.role(p) == SharedSecurity.Role.Sales
        }

    ///
    /// 予約詳細を表示する（US06 受入基準 1）。
    ///
    /// 存在しない予約番号では 404 を返す。**一覧へ戻る導線を必ず置く**ため、
    /// 空の応答ではなく画面を返す。行き止まりにすると利用者はブラウザの
    /// 戻るしか手段がなくなる。
    ///
    pub def show(req: Request): Response \ Session + CargoRepo + ShipperExistenceChecker =
        let bookingId = pathParam(req, "bookingId") |> Option.getWithDefault("");
        match BookingBookCargo.findVisible(Session.current(), bookingId) {
            case Some(c) => html(BookingPages.renderDetail(Session.current(), c,
                                                           shipperOf(c),
                                                           Session.csrfToken(), canBook()))
            case None    => SharedHttp.htmlWithStatus(404,
                BookingPages.renderNotFound(Session.current(), bookingId, Session.csrfToken()))
        }

    ///
    /// 経路設計へ引き渡す（US06 受入基準 2）。
    ///
    /// 成功時は PRG で詳細へ戻す。失敗時は詳細をそのまま描画し、理由を示す。
    /// **リダイレクトで理由を伝えない**のは、通知の仕組みが「発行された識別子」
    /// 用であり、失敗理由まで載せると Cookie の意味が増えるためである。
    ///
    /// 予約の荷主を ACL 経由で引く（Shipper のモデルには依存しない）
    def shipperOf(c: BookingModel.Cargo): Option[(String, String)] \ ShipperExistenceChecker =
        ShipperExistenceChecker.describeShipper(BookingModel.shipperIdOf(c))

    pub def assignToRouting(req: Request):
            Response \ Session + CargoRepo + ShipperExistenceChecker =
        let bookingId = pathParam(req, "bookingId") |> Option.getWithDefault("");
        match BookingAssignToRouting.assign(bookingId) {
            case BookingAssignToRouting.AssignResult.Assigned(_) =>
                redirect("/bookings/${bookingId}")
            case BookingAssignToRouting.AssignResult.NotFound =>
                SharedHttp.htmlWithStatus(404,
                    BookingPages.renderNotFound(Session.current(), bookingId, Session.csrfToken()))
            case BookingAssignToRouting.AssignResult.Invalid(_) =>
                // 現在の状態を読み直して描画する。**古い状態のまま出すと**、
                // 拒否された理由と画面の表示が食い違う
                match BookingBookCargo.findVisible(Session.current(), bookingId) {
                    case Some(c) => html(BookingPages.renderDetail(Session.current(), c,
                                                                   shipperOf(c),
                                                                   Session.csrfToken(),
                                                                   canBook()))
                    case None    => SharedHttp.htmlWithStatus(404,
                        BookingPages.renderNotFound(Session.current(), bookingId,
                                                    Session.csrfToken()))
                }
        }

    ///
    /// 貨物予約を登録する。
    ///
    /// 成功時は PRG で一覧へ戻す。
    ///
    pub def create(req: Request):
            Response \ Session + CargoRepo + IdGen + ShipperExistenceChecker + LocationCatalog + Clock + IO =
        let view = viewOf(req);
        let BookingPages.BookingFormView.BookingFormView(v) = view;
        let dimensions = parseDimensions(v#dimensionLength, v#dimensionWidth, v#dimensionHeight);
        match (parseCargoType(v#cargoType), parseWeightGrams(v#weight), parseQuantity(v#quantity)) {
            case (None, _, _) =>
                // **未知の種別を危険物へ倒さない**。安全側に見えるが、業務上は
                // 一般貨物が危険物として静かに登録される方が実害が大きい
                // （積載制限・取扱港・書類・料金がすべて変わる）
                invalidForm(view, ("cargoType", "貨物種別を選択してください"))
            case (_, None, _) =>
                invalidForm(view, ("weight", "重量は 0 より大きい数値（kg）で入力してください（例: 1200、12.5）"))
            case (_, _, Err(msg)) =>
                invalidForm(view, ("quantity", msg))
            case (Some(cargoType), Some(grams), Ok(quantity)) =>
                match dimensions {
                case Err(msg) => invalidForm(view, ("dimensions", msg))
                case Ok(dims) =>
                match specialRequirementsOf(cargoType, view) {
                case Err(e) => invalidForm(view, errorOf(e))
                case Ok((hazardous, temperature)) =>
                let cmd = command(v#shipperCode, v#origin, v#destination, v#arrivalDeadline,
                                  cargoType, grams, dims, quantity, optional(v#description),
                                  hazardous, temperature);
                match book(cmd) {
                    case BookResult.Booked(cargo) =>
                        // **発行された予約番号を通知として持たせる**。一覧に行が
                        // 増えるだけでは、どれが今登録したものか特定できない
                        withHeader(redirect("/bookings"), "Set-Cookie",
                            SharedHttpFlash.setCookie(
                                SharedHttpFlash.Flash.CargoBooked(BookingModel.bookingIdOf(cargo)),
                                SharedAppEnv.isSecureCookie()))
                    case BookResult.Invalid(e) => invalidForm(view, errorOf(e))
                }
                }
                }
        }

    ///
    /// 貨物種別に応じた特別要件を、フォームの入力から組み立てる。
    ///
    /// **入力があるかどうかで分岐しない**。種別が求めるものを組み立て、
    /// 種別が求めないものは組み立てない。入力の有無で決めると、
    /// 「危険物なのに欄を空で送った」場合に**要件なしで通ってしまう**
    /// （必須性の判断は集約が持つが、ここで空を `None` にすると集約へ届かない
    /// ではなく届いた上で弾かれる。届けた上で弾くのが正しい）。
    ///
    /// 種別に対応しない入力が送られてきた場合は**黙って捨てない**。捨てると、
    /// 利用者は申告したつもりで一般貨物として輸送される。
    ///
    def specialRequirementsOf(cargoType: BookingModel.CargoType, view: BookingFormView):
            Result[BookingError, (Option[BookingModel.HazardousDeclaration],
                                  Option[BookingModel.TemperatureRequirement])] =
        let BookingPages.BookingFormView.BookingFormView(v) = view;
        let hazardousEntered = String.trim(v#hazardousClass) != "" or
                               String.trim(v#unNumber) != "" or
                               String.trim(v#properShippingName) != "";
        let temperatureEntered = String.trim(v#minTemperature) != "" or
                                 String.trim(v#maxTemperature) != "";
        match cargoType {
            case BookingModel.CargoType.Hazardous =>
                if (not hazardousEntered)
                    Err(BookingError.HazardousDeclarationIsRequired)
                else
                    BookingModel.requireHazardousDeclaration(
                        v#hazardousClass, v#unNumber, v#properShippingName)
                        |> Result.map(d -> (Some(d), None))
            case BookingModel.CargoType.Refrigerated =>
                if (not temperatureEntered)
                    Err(BookingError.TemperatureRequirementIsRequired)
                else
                    parseTemperature(v#minTemperature, v#maxTemperature, v#temperatureUnit)
                        |> Result.map(t -> (None, Some(t)))
            case BookingModel.CargoType.General =>
                if (hazardousEntered)
                    Err(BookingError.HazardousDeclarationIsNotApplicable)
                else if (temperatureEntered)
                    Err(BookingError.TemperatureRequirementIsNotApplicable)
                else
                    Ok((None, None))
        }

    ///
    /// 温度の入力（度）を千分の一度の整数へ直してから組み立てる。
    ///
    /// 解釈は `SharedDecimal.parseSignedScaled` に委ねる。**独自に書かない**
    /// （IT4 で同じ変換を 2 箇所に書き、同じ符号消失バグを 2 つ作った）。
    /// 冷凍貨物の温度はほぼ常に負であり、符号を落とすと保冷条件が正反対になる。
    ///
    def parseTemperature(minText: String, maxText: String, unit: String):
            Result[BookingError, BookingModel.TemperatureRequirement] =
        match (SharedDecimal.parseSignedScaled(3, minText),
               SharedDecimal.parseSignedScaled(3, maxText)) {
            case (Some(lo), Some(hi)) =>
                BookingModel.requireTemperatureRequirement(lo, hi, unit)
            case _ => Err(BookingError.TemperatureIsMalformed)
        }

    ///
    /// 寸法（長さ・幅・高さ）を解釈する。
    ///
    /// **3 つで 1 組**として扱う。1 つだけ入力されたものを黙って捨てると、
    /// 利用者は入力したはずの値が保存されていないことに気付けない。
    ///
    /// 単位はセンチメートル。ドメインはミリメートルの整数で保持するため 10 倍する
    /// （固定小数点で持つ方針。浮動小数点の誤差を料金計算へ持ち込まない）。
    ///
    def parseDimensions(lengthCm: String, widthCm: String, heightCm: String):
            Result[String, Option[(Int32, Int32, Int32)]] =
        let entered = (String.trim(lengthCm), String.trim(widthCm), String.trim(heightCm));
        match entered {
            case ("", "", "") => Ok(None)
            case (l, w, h) =>
                match (parseCentimeters(l), parseCentimeters(w), parseCentimeters(h)) {
                    case (Some(a), Some(b), Some(c)) => Ok(Some((a, b, c)))
                    case _ => Err("寸法は長さ・幅・高さの 3 つを 0 より大きい数値（cm）で入力してください（例: 120）")
                }
        }

    ///
    /// センチメートルの入力をミリメートルの整数へ変換する。
    ///
    /// 解釈は `SharedDecimal.parseScaled` に委ねる。**独自に書かない**
    /// （IT4 で同じ変換を 2 箇所に書き、同じ符号消失バグを 2 つ作った）。
    ///
    def parseCentimeters(value: String): Option[Int32] =
        SharedDecimal.parseScaled(1, value)
            |> Option.flatMap(Int64.tryToInt32)
            |> Option.filter(n -> n > 0)

    /// 入力エラーとしてフォームを再描画する（入力値は保持する）
    def invalidForm(view: BookingFormView, error: (String, String)):
            Response \ Session + LocationCatalog =
        html(renderNew(Session.current(), withErrors(view, error), LocationCatalog.all(),
                       Session.csrfToken()))

    def viewOf(req: Request): BookingFormView =
        formOf(field(req, "shipperCode"), field(req, "origin"), field(req, "destination"),
               field(req, "arrivalDeadline"),
               formParam(req, "cargoType") |> Option.getWithDefault("GENERAL"),
               field(req, "weight"), field(req, "quantity"),
               field(req, "dimensionLength"), field(req, "dimensionWidth"),
               field(req, "dimensionHeight"), field(req, "description"),
               field(req, "hazardousClass"), field(req, "unNumber"),
               field(req, "properShippingName"),
               field(req, "minTemperature"), field(req, "maxTemperature"),
               formParam(req, "temperatureUnit") |> Option.getWithDefault("CELSIUS"), Nil)

    def field(req: Request, name: String): String =
        formParam(req, name) |> Option.getWithDefault("")

    def optional(value: String): Option[String] =
        if (String.trim(value) == "") None else Some(value)

    ///
    /// 貨物種別の入力を型へ変換する。
    ///
    /// **未知の値をどの種別へも倒さない**。当初は安全側のつもりで `Hazardous` へ
    /// 倒していたが、業務上は**一般貨物が危険物として静かに登録される方が実害が
    /// 大きい**（積載制限・取扱港・書類・料金がすべて変わり、後工程が誤った前提で動く）。
    /// しかも利用者には何も伝わらない。倒す先を持たないのが正しく、入力エラーとして返す。
    ///
    /// htmx の断片ルートは任意のクエリ値を受け付けるため、
    /// 「画面の選択肢以外は届かない」という前提は成り立たない。
    ///
    def parseCargoType(value: String): Option[CargoType] =
        BookingModel.cargoTypeFromPersisted(String.trim(value))

    ///
    /// 重量（kg の文字列）をグラムへ変換する。
    ///
    /// 解釈は `SharedDecimal.parseScaled` に委ねる。**0 とみなさない**。
    /// 0 とみなすと、打ち間違いが「重量 0 の予約」としてドメインの検証に回り、
    /// 利用者には何を直せばよいか伝わらない。
    ///
    def parseWeightGrams(value: String): Option[Int64] =
        SharedDecimal.parseScaled(3, value)

    ///
    /// 個数（任意）を解釈する。
    ///
    /// 未入力は `Ok(None)`、解釈できない入力は `Err(メッセージ)` を返す。
    /// 「10個」のような単位付きの入力に対して「1 以上の整数で」と返しても、
    /// **文字が混じっていることを指摘していない**ため直せない。
    ///
    def parseQuantity(value: String): Result[String, Option[Int32]] =
        let trimmed = String.trim(value);
        if (trimmed == "")
            Ok(None)
        else
            match SharedDecimal.parseNonNegativeInt(trimmed) {
                case Some(n) => Ok(Some(n))
                case None    => Err("個数は数字だけで入力してください（例: 5）")
            }

    /// ドメインの失敗理由を利用者の言葉へ翻訳する
    def errorOf(e: BookingError): (String, String) = match e {
        case BookingError.BookingIdIsRequired =>
            ("", "登録に失敗しました。時間をおいて再度お試しください")
        case BookingError.ShipperIdIsRequired =>
            ("shipperCode", "荷主コードを入力してください")
        case BookingError.ShipperNotFound =>
            ("shipperCode", "この荷主コードは登録されていません。荷主を登録してから予約してください")
        case BookingError.OriginIsRequired =>
            ("origin", "出発地を入力してください")
        case BookingError.DestinationIsRequired =>
            ("destination", "目的地を入力してください")
        case BookingError.OriginAndDestinationAreSame =>
            ("destination", "出発地と目的地が同じです。異なる港を指定してください")
        case BookingError.LocationCodeIsMalformed =>
            ("origin", "出発地・目的地を選択してください")
        case BookingError.LocationIsNotAvailable =>
            ("origin", "選択された港は取り扱いできません。一覧から選び直してください")
        case BookingError.ArrivalDeadlineIsRequired =>
            ("arrivalDeadline", "到着期限を入力してください")
        case BookingError.ArrivalDeadlineIsInPast =>
            ("arrivalDeadline", "到着期限は本日以降の日付を指定してください")
        case BookingError.ArrivalDeadlineIsMalformed =>
            ("arrivalDeadline", "到着期限に実在する日付を指定してください（例: 2026-12-31）")
        case BookingError.WeightIsRequired =>
            ("weight", "重量を入力してください")
        case BookingError.WeightIsNotPositive =>
            ("weight", "重量は 0 より大きい値を入力してください")
        case BookingError.CargoTypeIsMalformed =>
            ("cargoType", "貨物種別を選択してください")
        case BookingError.QuantityIsNotPositive =>
            ("quantity", "個数は 1 以上の整数で入力してください")
        case BookingError.DescriptionIsTooLong =>
            ("description", "品名は 500 文字以内で入力してください")
        // ---- 状態遷移（US06） ----
        case BookingError.CargoIsNotPreliminary =>
            ("", "この予約は仮受付ではないため、経路設計へ引き渡せません")
        // ---- 危険物申告・温度管理条件（US05） ----
        case BookingError.HazardousDeclarationIsRequired =>
            ("hazardousClass", "危険物を選んだ場合は危険物申告（クラス・UN 番号・正式輸送品名）を入力してください")
        case BookingError.HazardousDeclarationIsNotApplicable =>
            ("cargoType", "危険物申告は貨物種別が「危険物」のときだけ入力できます")
        case BookingError.HazardousClassIsRequired =>
            ("hazardousClass", "危険物クラスを入力してください（例: 3）")
        case BookingError.UnNumberIsMalformed =>
            ("unNumber", "UN 番号は UN と数字 4 桁で入力してください（例: UN1203）")
        case BookingError.ProperShippingNameIsRequired =>
            ("properShippingName", "正式輸送品名を入力してください")
        case BookingError.ProperShippingNameIsTooLong =>
            ("properShippingName", "正式輸送品名は 200 文字以内で入力してください")
        case BookingError.TemperatureRequirementIsRequired =>
            ("minTemperature", "冷凍・冷蔵貨物を選んだ場合は温度管理条件（最低温度・最高温度）を入力してください")
        case BookingError.TemperatureRequirementIsNotApplicable =>
            ("cargoType", "温度管理条件は貨物種別が「冷凍・冷蔵貨物」のときだけ入力できます")
        case BookingError.TemperatureIsMalformed =>
            ("minTemperature", "温度は数値で入力してください（例: -25、-18.5）")
        case BookingError.TemperatureRangeIsInverted =>
            ("minTemperature", "最低温度は最高温度以下で入力してください")
        case BookingError.TemperatureUnitIsMalformed =>
            ("temperatureUnit", "温度の単位を選択してください")
        case BookingError.DimensionIsNotPositive =>
            ("", "寸法は 0 より大きい値を入力してください")
    }

    def withErrors(view: BookingFormView, error: (String, String)): BookingFormView =
        let BookingPages.BookingFormView.BookingFormView(v) = view;
        formOf(v#shipperCode, v#origin, v#destination, v#arrivalDeadline, v#cargoType,
               v#weight, v#quantity, v#dimensionLength, v#dimensionWidth,
               v#dimensionHeight, v#description, v#hazardousClass, v#unNumber,
               v#properShippingName, v#minTemperature, v#maxTemperature,
               v#temperatureUnit, error :: Nil)
}
