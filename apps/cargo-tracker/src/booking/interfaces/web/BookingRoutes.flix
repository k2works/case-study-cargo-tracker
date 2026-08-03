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
    use SharedHttpAuth.redirect
    use SharedIdGen.IdGen
    use SharedSession.Session
    use ShipperExistenceChecker.ShipperExistenceChecker

    /// 登録フォームを表示する
    pub def newPage(_req: Request): Response \ Session =
        html(renderNew(Session.current(), emptyForm(), Session.csrfToken()))

    /// 貨物種別に応じた追加入力欄（htmx）。US05 の拡張点
    pub def cargoTypeFields(req: Request): Response =
        let cargoType = queryParam(req, "cargoType") |> Option.getWithDefault("GENERAL");
        html(renderCargoTypeFields(formOf("", "", "", "", cargoType, "", "", "", Nil)))

    /// 貨物予約を一覧する
    pub def index(_req: Request): Response \ Session + CargoRepo =
        html(renderIndex(Session.current(), listAll(), Session.csrfToken()))

    ///
    /// 貨物予約を登録する。
    ///
    /// 成功時は PRG で一覧へ戻す。
    ///
    pub def create(req: Request): Response \ Session + CargoRepo + IdGen + ShipperExistenceChecker =
        let view = viewOf(req);
        let BookingPages.BookingFormView.BookingFormView(v) = view;
        match parseWeightGrams(v#weight) {
            case None =>
                html(renderNew(Session.current(),
                    withErrors(view, ("weight", "重量を数値（kg）で入力してください")),
                    Session.csrfToken()))
            case Some(grams) =>
                let cmd = command(v#shipperCode, v#origin, v#destination, v#arrivalDeadline,
                                  parseCargoType(v#cargoType), grams, None,
                                  parseQuantity(v#quantity), optional(v#description));
                match book(cmd) {
                    case BookResult.Booked(_)  => redirect("/bookings")
                    case BookResult.Invalid(e) =>
                        html(renderNew(Session.current(), withErrors(view, errorOf(e)),
                                       Session.csrfToken()))
                }
        }

    def viewOf(req: Request): BookingFormView =
        formOf(field(req, "shipperCode"), field(req, "origin"), field(req, "destination"),
               field(req, "arrivalDeadline"),
               formParam(req, "cargoType") |> Option.getWithDefault("GENERAL"),
               field(req, "weight"), field(req, "quantity"), field(req, "description"), Nil)

    def field(req: Request, name: String): String =
        formParam(req, name) |> Option.getWithDefault("")

    def optional(value: String): Option[String] =
        if (String.trim(value) == "") None else Some(value)

    ///
    /// 貨物種別の入力を型へ変換する。
    ///
    /// **未知の値を一般貨物へ倒さない**。危険物として登録したつもりの貨物が
    /// 一般貨物として扱われると、指定港以外での取扱いが起きる。
    /// 解釈できない場合は `Hazardous` でも `General` でもなく、
    /// 検証で弾かれる形（後段の `bookCargo` は種別を検証しないため、ここで倒す先を
    /// 持たない）を避けるべく、**画面の選択肢以外は届かない**前提を保つ。
    /// 万一届いた場合は一般貨物として扱わず、危険側（Hazardous）へ倒す。
    ///
    def parseCargoType(value: String): CargoType =
        match BookingModel.cargoTypeFromPersisted(value) {
            case Some(t) => t
            case None    => CargoType.Hazardous
        }

    ///
    /// 重量（kg の文字列）をグラムへ変換する。
    ///
    /// 解釈できない入力は `None` を返し、入力エラーとして扱う。
    /// **0 とみなさない**。0 とみなすと、打ち間違いが「重量 0 の予約」として
    /// ドメインの検証に回り、利用者には「重量が正しくありません」としか伝わらない。
    ///
    def parseWeightGrams(value: String): Option[Int64] =
        let trimmed = String.trim(value);
        if (trimmed == "")
            None
        else
            match String.indexOf(substr = ".", trimmed) {
                case None => Option.map(n -> Int32.toInt64(n) * 1000i64, Int32.fromString(trimmed))
                case Some(i) =>
                    let whole = String.slice(start = 0, end = i, trimmed);
                    let rawFraction = String.slice(start = i + 1, end = String.length(trimmed), trimmed);
                    // グラム単位（小数第 3 位）まで見る
                    let fraction = String.padRight(3, '0', String.takeLeft(3, rawFraction));
                    match (Int32.fromString(whole), Int32.fromString(fraction)) {
                        case (Some(w), Some(f)) => Some(Int32.toInt64(w) * 1000i64 + Int32.toInt64(f))
                        case _                  => None
                    }
            }

    /// 個数（任意）。解釈できない値は 0 に倒し、ドメインの検証で弾く
    def parseQuantity(value: String): Option[Int32] =
        let trimmed = String.trim(value);
        if (trimmed == "")
            None
        else
            match Int32.fromString(trimmed) {
                case Some(n) => Some(n)
                case None    => Some(0)
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
            ("origin", "港コードは半角英数字 5 文字で入力してください（例: JPOSA）")
        case BookingError.ArrivalDeadlineIsRequired =>
            ("arrivalDeadline", "到着期限を入力してください")
        case BookingError.ArrivalDeadlineIsMalformed =>
            ("arrivalDeadline", "到着期限は YYYY-MM-DD の形式で入力してください（例: 2026-12-31）")
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
               v#weight, v#quantity, v#description, error :: Nil)
}
