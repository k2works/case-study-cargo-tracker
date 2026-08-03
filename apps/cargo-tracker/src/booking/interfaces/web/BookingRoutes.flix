///
/// 貨物予約のルートハンドラ（US04）。
///
mod BookingRoutes {
    use BookingBookCargo.{BookResult, book, command, listAll}
    use BookingModel.{CargoType, BookingError}
    use BookingPages.{BookingFormView, emptyForm, formOf, renderNew, renderIndex,
                      renderCargoTypeFields}
    use CargoRepo.CargoRepo
    use SharedHttp.{Request, Response, formParam, queryParam, html}
    use SharedHttpAuth.{redirect, withHeader}
    use SharedIdGen.IdGen
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
        let view = formOf(shipperCode, "", "", "", "GENERAL", "", "", "", "", "", "", Nil);
        html(renderNew(Session.current(), view, LocationCatalog.all(), Session.csrfToken()))

    /// 貨物種別に応じた追加入力欄（htmx）。US05 の拡張点
    pub def cargoTypeFields(req: Request): Response =
        let cargoType = queryParam(req, "cargoType") |> Option.getWithDefault("GENERAL");
        html(renderCargoTypeFields(formOf("", "", "", "", cargoType, "", "", "", "", "", "", Nil)))

    ///
    /// 貨物予約を一覧する。
    ///
    /// PRG の遷移先であり、直前の予約結果を通知として出す。**出したら消す**。
    ///
    pub def index(req: Request): Response \ Session + CargoRepo + IO =
        let flash = SharedHttpFlash.fromRequest(req)
            |> Option.filter(f -> match f {
                // **予約一覧に出すのは予約の通知だけ**。荷主登録の通知が
                // ここに出ると、利用者は何が起きたのか分からなくなる
                case SharedHttpFlash.Flash.CargoBooked(_) => true
                case _                                    => false
               });
        let notice = Option.map(SharedHttpFlash.message, flash);
        let page = html(renderIndex(Session.current(), listAll(), Session.csrfToken(), notice));
        match flash {
            case None    => page
            case Some(_) => withHeader(page, "Set-Cookie",
                                       SharedHttpFlash.clearCookie(SharedAppEnv.isSecureCookie()))
        }

    ///
    /// 貨物予約を登録する。
    ///
    /// 成功時は PRG で一覧へ戻す。
    ///
    pub def create(req: Request):
            Response \ Session + CargoRepo + IdGen + ShipperExistenceChecker + LocationCatalog + IO =
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
                let cmd = command(v#shipperCode, v#origin, v#destination, v#arrivalDeadline,
                                  cargoType, grams, dims, quantity, optional(v#description));
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
               field(req, "dimensionHeight"), field(req, "description"), Nil)

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
        case BookingError.DimensionIsNotPositive =>
            ("", "寸法は 0 より大きい値を入力してください")
    }

    def withErrors(view: BookingFormView, error: (String, String)): BookingFormView =
        let BookingPages.BookingFormView.BookingFormView(v) = view;
        formOf(v#shipperCode, v#origin, v#destination, v#arrivalDeadline, v#cargoType,
               v#weight, v#quantity, v#dimensionLength, v#dimensionWidth,
               v#dimensionHeight, v#description, error :: Nil)
}
