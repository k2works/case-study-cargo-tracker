///
/// 荷主のルートハンドラ（US02・US03）。
///
/// ここでの責務は 3 つに限る。
///
/// 1. リクエストから入力を取り出す
/// 2. ユースケースを呼ぶ
/// 3. 結果を画面へ写す（`ShipperError` を**利用者の言葉**へ翻訳する）
///
/// 判断そのものはユースケースとドメインにある。ここに業務ルールを書かない。
///
mod ShipperRoutes {
    use SharedHtml.render
    use SharedHttp.{Request, Response, formParam, queryParam, html}
    use SharedHttpAuth.redirect
    use SharedSession.Session
    use SharedIdGen.IdGen
    use ShipperModel.ShipperError
    use ShipperPages.{ShipperFormView, emptyForm, formOf, renderNew, renderIndex,
                      renderDuplicate, renderCorporateFields}
    use ShipperRegister.{RegisterResult, register, listAll}
    use ShipperRepo.ShipperRepo

    /// 登録フォームを表示する
    pub def newPage(_req: Request): Response \ Session =
        html(renderNew(Session.current(), emptyForm(), Session.csrfToken()))

    ///
    /// 法人固有フィールドの部分更新（htmx）。
    ///
    /// 選択された種別はクエリパラメータで届く。**フォーム全体を再描画しない**ため、
    /// 入力途中の他の項目は失われない。
    ///
    pub def corporateFields(req: Request): Response =
        let shipperType = queryParam(req, "shipperType") |> Option.getWithDefault("INDIVIDUAL");
        let view = formOf("", "", "", "", shipperType, "", "", Nil);
        html(renderCorporateFields(view))

    /// 荷主を一覧する
    pub def index(_req: Request): Response \ Session + ShipperRepo =
        html(renderIndex(Session.current(), listAll(), Session.csrfToken()))

    ///
    /// 荷主を登録する。
    ///
    /// 成功時は PRG で一覧へ戻す。結果をそのまま描画すると、再読み込みで
    /// フォームが再送信される。
    ///
    pub def create(req: Request): Response \ Session + ShipperRepo + IdGen =
        let view = viewOf(req);
        let ShipperPages.ShipperFormView.ShipperFormView(v) = view;
        let cmd =
            if (v#shipperType == "CORPORATE")
                ShipperRegister.corporateCommand(v#name, v#email, optional(v#phone),
                                                 optional(v#address), v#contractNumber,
                                                 parseDiscountRate(v#discountRate))
            else
                ShipperRegister.individualCommand(v#name, v#email, optional(v#phone),
                                                  optional(v#address));
        match register(cmd) {
            case RegisterResult.Registered(_) => redirect("/shippers")
            case RegisterResult.DuplicateEmail(existing) =>
                html(renderDuplicate(Session.current(), existing, view, Session.csrfToken()))
            case RegisterResult.Invalid(e) =>
                let withError = withErrors(view, errorOf(e));
                html(renderNew(Session.current(), withError, Session.csrfToken()))
        }

    /// リクエストの入力をフォームへ写す
    def viewOf(req: Request): ShipperFormView =
        formOf(field(req, "name"), field(req, "email"), field(req, "phone"),
               field(req, "address"),
               formParam(req, "shipperType") |> Option.getWithDefault("INDIVIDUAL"),
               field(req, "contractNumber"), field(req, "discountRate"), Nil)

    def field(req: Request, name: String): String =
        formParam(req, name) |> Option.getWithDefault("")

    /// 空文字は未入力として `None` に倒す
    def optional(value: String): Option[String] =
        if (String.trim(value) == "") None else Some(value)

    ///
    /// 割引率の入力（百分率の文字列）を万分率の整数へ変換する。
    ///
    /// `15` も `15.5` も受け取る。解釈できない入力は**範囲外の値**へ倒し、
    /// ドメインの検証で `DiscountRateOutOfRange` として弾かれるようにする。
    /// ここで「0 とみなす」と、打ち間違いが**割引なしの法人**として静かに通る。
    ///
    def parseDiscountRate(value: String): Int32 =
        let trimmed = String.trim(value);
        let outOfRange = -1;
        match String.indexOf(substr = ".", trimmed) {
            case None =>
                match Int32.fromString(trimmed) {
                    case Some(n) => n * 100
                    case None    => outOfRange
                }
            case Some(i) =>
                let whole = String.slice(start = 0, end = i, trimmed);
                let rawFraction = String.slice(start = i + 1, end = String.length(trimmed), trimmed);
                // 小数第 2 位までを見る。`15.5` は `15.50` と同じ
                let fraction = String.padRight(2, '0', String.takeLeft(2, rawFraction));
                match (Int32.fromString(whole), Int32.fromString(fraction)) {
                    case (Some(w), Some(f)) => w * 100 + f
                    case _                  => outOfRange
                }
        }

    ///
    /// ドメインの失敗理由を**利用者の言葉**へ翻訳する。
    ///
    /// 内部語（`NameIsRequired`）を画面に出さない。返すのは
    /// 「どのフィールドの」「何が問題か」の組であり、`formField` が該当欄の下に出す。
    ///
    def errorOf(e: ShipperError): (String, String) = match e {
        case ShipperError.ShipperIdIsRequired =>
            ("", "登録に失敗しました。時間をおいて再度お試しください")
        case ShipperError.NameIsRequired =>
            ("name", "氏名 / 社名を入力してください")
        case ShipperError.NameIsTooLong =>
            ("name", "氏名 / 社名は 200 文字以内で入力してください")
        case ShipperError.EmailIsRequired =>
            ("email", "メールアドレスを入力してください")
        case ShipperError.EmailIsMalformed =>
            ("email", "メールアドレスの形式が正しくありません（例: yamada@example.com）")
        case ShipperError.AddressIsTooLong =>
            ("address", "住所は 500 文字以内で入力してください")
        case ShipperError.PhoneIsTooLong =>
            ("phone", "電話番号は 50 文字以内で入力してください")
        case ShipperError.ContractNumberIsRequired =>
            ("contractNumber", "法人の場合は契約番号を入力してください")
        case ShipperError.ContractNumberIsTooLong =>
            ("contractNumber", "契約番号は 50 文字以内で入力してください")
        case ShipperError.DiscountRateOutOfRange =>
            ("discountRate", "割引率は 0 〜 30 の範囲で入力してください")
    }

    /// フォームにエラーを添える
    def withErrors(view: ShipperFormView, error: (String, String)): ShipperFormView =
        let ShipperPages.ShipperFormView.ShipperFormView(v) = view;
        formOf(v#name, v#email, v#phone, v#address, v#shipperType,
               v#contractNumber, v#discountRate, error :: Nil)
}
