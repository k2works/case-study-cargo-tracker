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
        if (v#shipperType == "CORPORATE")
            match parseDiscountRate(v#discountRate) {
                // **解釈できない入力を 0 とみなさない**。0 にすると打ち間違いが
                // 「割引なしの法人」として静かに通る
                case None => invalidForm(view,
                    ("discountRate", "割引率は 0 〜 30 の数値で入力してください（例: 15、7.5）"))
                case Some(rate) =>
                    submit(view, ShipperRegister.corporateCommand(
                        v#name, v#email, optional(v#phone), optional(v#address),
                        v#contractNumber, rate))
            }
        else
            submit(view, ShipperRegister.individualCommand(
                v#name, v#email, optional(v#phone), optional(v#address)))

    /// ユースケースを呼び、結果を画面へ写す
    def submit(view: ShipperFormView,
               cmd: ShipperRegister.RegisterCommand): Response \ Session + ShipperRepo + IdGen =
        match register(cmd) {
            case RegisterResult.Registered(_) => redirect("/shippers")
            case RegisterResult.DuplicateEmail(existing) =>
                html(renderDuplicate(Session.current(), existing, view, Session.csrfToken()))
            case RegisterResult.Invalid(e) => invalidForm(view, errorOf(e))
        }

    /// 入力エラーとしてフォームを再描画する（入力値は保持する）
    def invalidForm(view: ShipperFormView, error: (String, String)): Response \ Session =
        html(renderNew(Session.current(), withErrors(view, error), Session.csrfToken()))

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
    /// 解釈は `SharedDecimal.parseScaled` に委ねる。IT4 のレビューで
    /// **負の小数の符号が消える**バグ（`-0.5` が `0.50%` になる）が見つかり、
    /// 同じ処理を書いていた予約側と合わせて共有モジュールへ寄せた。
    ///
    def parseDiscountRate(value: String): Option[Int32] =
        Option.flatMap(v -> Int64.tryToInt32(v), SharedDecimal.parseScaled(2, value))

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
