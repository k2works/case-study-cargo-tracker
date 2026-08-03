///
/// 貨物予約の画面（`Html` を返す純粋関数）。
///
mod BookingPages {
    use BookingModel.{Cargo, CargoType}
    use SharedHtml.{Html, element, text, empty, attr, class_, id}
    use SharedHtmlComponents.{form, formField, alert}
    use SharedHtmlLayout.renderPage
    use SharedSecurity.Principal

    ///
    /// 入力途中の予約フォーム。
    ///
    /// **数値も文字列のまま持つ**。`1,200` のような入力をそのまま画面へ戻せなければ、
    /// 利用者は何を直せばよいか分からない。
    ///
    pub enum BookingFormView({
        shipperCode = String,
        origin = String,
        destination = String,
        arrivalDeadline = String,
        cargoType = String,
        weight = String,
        quantity = String,
        description = String,
        errors = List[(String, String)]
    })

    pub def emptyForm(): BookingFormView =
        formOf("", "", "", "", "GENERAL", "", "", "", Nil)

    pub def formOf(shipperCode: String, origin: String, destination: String,
                   arrivalDeadline: String, cargoType: String, weight: String,
                   quantity: String, description: String,
                   errors: List[(String, String)]): BookingFormView =
        BookingFormView.BookingFormView({
            shipperCode = shipperCode, origin = origin, destination = destination,
            arrivalDeadline = arrivalDeadline, cargoType = cargoType, weight = weight,
            quantity = quantity, description = description, errors = errors
        })

    def errorsFor(view: BookingFormView, field: String): List[String] =
        let BookingFormView.BookingFormView(v) = view;
        v#errors |> List.filterMap(pair -> {
            let (f, msg) = pair;
            if (f == field) Some(msg) else None
        })

    /// 貨物種別の選択肢 1 つ
    def typeOption(t: CargoType, selected: String): Html =
        let value = BookingModel.cargoTypeToPersisted(t);
        let attrs = attr("value", value) ::
                    (if (value == selected) attr("selected", "selected") :: Nil else Nil);
        element("option", attrs, text(BookingModel.cargoTypeLabel(t)) :: Nil)

    ///
    /// 貨物種別の選択。
    ///
    /// **US05（危険物・冷凍貨物）の拡張点をここに用意する**。種別の変更で
    /// `#cargo-type-fields` を差し替える形にしておけば、IT5 では
    /// 「差し替え先の中身を作る」だけで済み、フォーム全体を作り直さずに済む。
    ///
    def cargoTypeSelect(view: BookingFormView): Html =
        let BookingFormView.BookingFormView(v) = view;
        element("div", class_("mb-3") :: Nil,
            element("label", attr("for", "cargoType") :: class_("form-label") :: Nil,
                text("貨物種別") :: Nil) ::
            element("select",
                id("cargoType") :: attr("name", "cargoType") :: class_("form-select") ::
                attr("hx-get", "/bookings/new/cargo-type-fields") ::
                attr("hx-target", "#cargo-type-fields") ::
                attr("hx-swap", "innerHTML") ::
                attr("hx-trigger", "change") :: Nil,
                BookingModel.allCargoTypes() |> List.map(t -> typeOption(t, v#cargoType))) :: Nil)

    ///
    /// 貨物種別に応じた追加入力欄。
    ///
    /// **IT4 では常に空**である。US05 で危険物申告（クラス・UN 番号・品名）と
    /// 温度管理条件（最低・最高・単位）をここへ差す。
    ///
    pub def cargoTypeFields(_view: BookingFormView): Html = empty()

    /// 予約登録フォーム
    pub def newForm(view: BookingFormView, csrfToken: Option[String]): Html =
        let BookingFormView.BookingFormView(v) = view;
        let globalErrors = errorsFor(view, "") |> List.map(msg -> alert("danger", msg));
        element("div", class_("row") :: Nil,
            element("div", class_("col-md-8 offset-md-2") :: Nil,
                element("h1", class_("h4 mb-3") :: Nil, text("貨物予約登録") :: Nil) ::
                globalErrors :::
                form("/bookings", "post", csrfToken,
                    formField("shipperCode", "荷主コード（一覧に表示される SHP- で始まるコード）", v#shipperCode,
                              errorsFor(view, "shipperCode")) ::
                    shipperHint() ::
                    formField("origin", "出発地（UN/LOCODE 5 文字）", v#origin,
                              errorsFor(view, "origin")) ::
                    formField("destination", "目的地（UN/LOCODE 5 文字）", v#destination,
                              errorsFor(view, "destination")) ::
                    formField("arrivalDeadline", "到着期限（YYYY-MM-DD）", v#arrivalDeadline,
                              errorsFor(view, "arrivalDeadline")) ::
                    cargoTypeSelect(view) ::
                    element("div", id("cargo-type-fields") :: Nil,
                        cargoTypeFields(view) :: Nil) ::
                    formField("weight", "重量（kg）", v#weight, errorsFor(view, "weight")) ::
                    formField("quantity", "個数（任意）", v#quantity, errorsFor(view, "quantity")) ::
                    formField("description", "品名（任意）", v#description,
                              errorsFor(view, "description")) ::
                    element("div", class_("d-flex gap-2") :: Nil,
                        element("button", class_("btn btn-primary") ::
                                          attr("type", "submit") :: Nil,
                            text("登録する") :: Nil) ::
                        element("a", class_("btn btn-outline-secondary") ::
                                     attr("href", "/bookings") :: Nil,
                            text("キャンセル") :: Nil) :: Nil) :: Nil) :: Nil) :: Nil)

    ///
    /// 荷主が未登録のときの導線（US04 受入条件 8・Try T4）。
    ///
    /// 予約しようとして荷主がいないと分かった利用者は、**そこで手が止まる**。
    /// 荷主登録への導線を欄の直下に置き、戻ってこられることを明記する。
    ///
    def shipperHint(): Html =
        element("p", class_("form-text mb-3") :: Nil,
            text("荷主が未登録の場合は ") ::
            element("a", attr("href", "/shippers/new") :: Nil,
                text("荷主を登録") :: Nil) ::
            text(" してから、この画面に戻って予約してください") :: Nil)

    /// 一覧の 1 行
    def bookingRow(c: Cargo): Html =
        let spec = BookingModel.routeSpecificationOf(c);
        element("tr", Nil,
            element("td", Nil, text(BookingModel.bookingIdOf(c)) :: Nil) ::
            element("td", Nil, text(BookingModel.originOf(spec)) :: Nil) ::
            element("td", Nil, text(BookingModel.destinationOf(spec)) :: Nil) ::
            element("td", Nil, text(BookingModel.arrivalDeadlineOf(spec)) :: Nil) ::
            element("td", Nil,
                text(BookingModel.cargoTypeLabel(BookingModel.cargoTypeOf(c))) :: Nil) ::
            element("td", Nil,
                text(BookingModel.weightKilograms(BookingModel.weightGramsOf(c)) + " kg") :: Nil) ::
            element("td", Nil,
                element("span",
                    class_(BookingModel.statusBadgeClass(BookingModel.bookingStatusOf(c))) :: Nil,
                    text(BookingModel.statusLabel(BookingModel.bookingStatusOf(c))) :: Nil) :: Nil) :: Nil)

    /// 貨物予約一覧
    pub def index(cargos: List[Cargo]): Html =
        let body =
            if (List.isEmpty(cargos))
                element("p", class_("text-muted") :: Nil,
                    text("登録されている貨物予約はありません。[新規予約登録] から登録してください") :: Nil)
            else
                element("table", class_("table table-striped align-middle") :: Nil,
                    element("thead", Nil,
                        element("tr", Nil,
                            element("th", Nil, text("予約番号") :: Nil) ::
                            element("th", Nil, text("出発地") :: Nil) ::
                            element("th", Nil, text("目的地") :: Nil) ::
                            element("th", Nil, text("到着期限") :: Nil) ::
                            element("th", Nil, text("貨物種別") :: Nil) ::
                            element("th", Nil, text("重量") :: Nil) ::
                            element("th", Nil, text("状態") :: Nil) :: Nil) :: Nil) ::
                    element("tbody", id("booking-list") :: Nil,
                        List.map(bookingRow, cargos)) :: Nil);
        element("div", Nil,
            element("div", class_("d-flex justify-content-between align-items-center mb-3") :: Nil,
                element("h1", class_("h4 mb-0") :: Nil, text("貨物予約一覧") :: Nil) ::
                element("a", class_("btn btn-primary") ::
                             attr("href", "/bookings/new") :: Nil,
                    text("新規予約登録") :: Nil) :: Nil) ::
            body :: Nil)

    pub def renderNew(p: Option[Principal], view: BookingFormView,
                      csrfToken: Option[String]): String =
        renderPage("貨物予約登録", p, "/bookings", csrfToken, newForm(view, csrfToken))

    pub def renderIndex(p: Option[Principal], cargos: List[Cargo],
                        csrfToken: Option[String]): String =
        renderPage("貨物予約一覧", p, "/bookings", csrfToken, index(cargos))

    pub def renderCargoTypeFields(view: BookingFormView): String =
        SharedHtml.render(cargoTypeFields(view))
}
