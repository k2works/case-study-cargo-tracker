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
        dimensionLength = String,
        dimensionWidth = String,
        dimensionHeight = String,
        description = String,
        hazardousClass = String,
        unNumber = String,
        properShippingName = String,
        minTemperature = String,
        maxTemperature = String,
        temperatureUnit = String,
        errors = List[(String, String)]
    })

    pub def emptyForm(): BookingFormView =
        formOf("", "", "", "", "GENERAL", "", "", "", "", "", "",
               "", "", "", "", "", "CELSIUS", Nil)

    pub def formOf(shipperCode: String, origin: String, destination: String,
                   arrivalDeadline: String, cargoType: String, weight: String,
                   quantity: String, dimensionLength: String, dimensionWidth: String,
                   dimensionHeight: String, description: String,
                   hazardousClass: String, unNumber: String, properShippingName: String,
                   minTemperature: String, maxTemperature: String, temperatureUnit: String,
                   errors: List[(String, String)]): BookingFormView =
        BookingFormView.BookingFormView({
            shipperCode = shipperCode, origin = origin, destination = destination,
            arrivalDeadline = arrivalDeadline, cargoType = cargoType, weight = weight,
            quantity = quantity, dimensionLength = dimensionLength,
            dimensionWidth = dimensionWidth, dimensionHeight = dimensionHeight,
            description = description,
            hazardousClass = hazardousClass, unNumber = unNumber,
            properShippingName = properShippingName,
            minTemperature = minTemperature, maxTemperature = maxTemperature,
            temperatureUnit = temperatureUnit, errors = errors
        })

    def errorsFor(view: BookingFormView, field: String): List[String] =
        let BookingFormView.BookingFormView(v) = view;
        v#errors |> List.filterMap(pair -> {
            let (f, msg) = pair;
            if (f == field) Some(msg) else None
        })

    ///
    /// 場所の選択。
    ///
    /// **UN/LOCODE を手入力させない**。取り扱えるのはマスタにある港だけであり、
    /// 営業担当者に 5 文字のコードの暗記を求めるのは業務の要求ではない。
    /// 港名を添えて選ばせる（`東京 (JPTYO)`）。
    ///
    def locationSelect(name: String, label: String, selected: String,
                       locations: List[(String, String)], errors: List[String]): Html =
        let hasError = not List.isEmpty(errors);
        let cls = if (hasError) "form-select is-invalid" else "form-select";
        let blank = element("option", attr("value", "") :: Nil, text("選択してください") :: Nil);
        let options = locations |> List.map(match (code, locationName) -> {
            let attrs = attr("value", code) ::
                        (if (code == selected) attr("selected", "selected") :: Nil else Nil);
            element("option", attrs, text("${locationName} (${code})") :: Nil)
        });
        element("div", class_("mb-3") :: Nil,
            element("label", attr("for", name) :: class_("form-label") :: Nil,
                text(label) :: Nil) ::
            element("select", id(name) :: attr("name", name) :: class_(cls) :: Nil,
                blank :: options) ::
            (errors |> List.map(msg ->
                element("div", class_("invalid-feedback") :: Nil, text(msg) :: Nil))))

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
        // **エラーを描かないと、選択欄のエラーが画面に出ない**。`formField` と違い
        // `select` は自前で組み立てるため、エラー表示も自前で書く必要がある
        // （IT4 のテストで、未知の種別のメッセージが画面に出ていないことを検出した）
        let errors = errorsFor(view, "cargoType");
        let cls = if (List.isEmpty(errors)) "form-select" else "form-select is-invalid";
        element("div", class_("mb-3") :: Nil,
            element("label", attr("for", "cargoType") :: class_("form-label") :: Nil,
                text("貨物種別") :: Nil) ::
            element("select",
                id("cargoType") :: attr("name", "cargoType") :: class_(cls) ::
                attr("hx-get", "/bookings/new/cargo-type-fields") ::
                attr("hx-target", "#cargo-type-fields") ::
                attr("hx-swap", "innerHTML") ::
                attr("hx-trigger", "change") :: Nil,
                BookingModel.allCargoTypes() |> List.map(t -> typeOption(t, v#cargoType))) ::
            (errors |> List.map(msg ->
                element("div", class_("invalid-feedback") :: Nil, text(msg) :: Nil))))

    ///
    /// 貨物種別に応じた追加入力欄。
    ///
    /// **IT4 では常に空**である。US05 で危険物申告（クラス・UN 番号・品名）と
    /// 温度管理条件（最低・最高・単位）をここへ差す。
    ///
    pub def cargoTypeFields(view: BookingFormView): Html =
        let BookingFormView.BookingFormView(v) = view;
        match v#cargoType {
            case "HAZARDOUS"    => hazardousFields(view)
            case "REFRIGERATED" => temperatureFields(view)
            // **種別に対応しない欄を出さない**。出しっぱなしにすると、
            // 一般貨物の登録で「UN 番号は？」と利用者が迷う
            case _              => empty()
        }

    /// 危険物申告の入力欄（US05 受入基準 1）
    def hazardousFields(view: BookingFormView): Html =
        let BookingFormView.BookingFormView(v) = view;
        element("div", Nil,
            formField("hazardousClass", "危険物クラス", v#hazardousClass,
                      errorsFor(view, "hazardousClass")) ::
            formField("unNumber", "UN 番号（例: UN1203）", v#unNumber,
                      errorsFor(view, "unNumber")) ::
            formField("properShippingName", "正式輸送品名", v#properShippingName,
                      errorsFor(view, "properShippingName")) :: Nil)

    /// 温度管理条件の入力欄（US05 受入基準 2）
    def temperatureFields(view: BookingFormView): Html =
        let BookingFormView.BookingFormView(v) = view;
        element("div", Nil,
            formField("minTemperature", "最低温度（例: -25.5）", v#minTemperature,
                      errorsFor(view, "minTemperature")) ::
            formField("maxTemperature", "最高温度（例: -18）", v#maxTemperature,
                      errorsFor(view, "maxTemperature")) ::
            temperatureUnitSelect(view) :: Nil)

    /// 温度の単位（摂氏 / 華氏）
    def temperatureUnitSelect(view: BookingFormView): Html =
        let BookingFormView.BookingFormView(v) = view;
        let selected = if (v#temperatureUnit == "FAHRENHEIT") "FAHRENHEIT" else "CELSIUS";
        element("div", class_("mb-3") :: Nil,
            element("label", attr("for", "temperatureUnit") :: class_("form-label") :: Nil,
                text("温度の単位") :: Nil) ::
            element("select", id("temperatureUnit") :: attr("name", "temperatureUnit") ::
                             class_("form-select") :: Nil,
                unitOption("CELSIUS", "摂氏（℃）", selected) ::
                unitOption("FAHRENHEIT", "華氏（℉）", selected) :: Nil) :: Nil)

    def unitOption(value: String, label: String, selected: String): Html =
        let attrs = attr("value", value) ::
                    (if (value == selected) attr("selected", "selected") :: Nil else Nil);
        element("option", attrs, text(label) :: Nil)

    /// 予約登録フォーム
    pub def newForm(view: BookingFormView, locations: List[(String, String)],
                    csrfToken: Option[String]): Html =
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
                    locationSelect("origin", "出発地", v#origin, locations,
                                   errorsFor(view, "origin")) ::
                    locationSelect("destination", "目的地", v#destination, locations,
                                   errorsFor(view, "destination")) ::
                    dateField("arrivalDeadline", "到着期限", v#arrivalDeadline,
                              errorsFor(view, "arrivalDeadline")) ::
                    cargoTypeSelect(view) ::
                    element("div", id("cargo-type-fields") :: Nil,
                        cargoTypeFields(view) :: Nil) ::
                    formField("weight", "重量（kg）", v#weight, errorsFor(view, "weight")) ::
                    formField("quantity", "個数（任意）", v#quantity, errorsFor(view, "quantity")) ::
                    // 寸法は 3 つで 1 組。ドメインと DB 列は IT4 で用意していたが
                    // **フォームに欄がなく常に未入力で保存されていた**（レビュー M8）
                    formField("dimensionLength", "長さ（cm・任意）", v#dimensionLength,
                              errorsFor(view, "dimensions")) ::
                    formField("dimensionWidth", "幅（cm・任意）", v#dimensionWidth,
                              errorsFor(view, "dimensions")) ::
                    formField("dimensionHeight", "高さ（cm・任意）", v#dimensionHeight,
                              errorsFor(view, "dimensions")) ::
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

    ///
    /// 日付の入力欄。
    ///
    /// `type="date"` にするのは、到着期限が荷主との会話中に「再来週の金曜」と
    /// 決まることが多く、`YYYY-MM-DD` を暗算で組み立てるのが事故のもとだからである。
    ///
    def dateField(name: String, label: String, value: String, errors: List[String]): Html =
        let hasError = not List.isEmpty(errors);
        let cls = if (hasError) "form-control is-invalid" else "form-control";
        element("div", class_("mb-3") :: Nil,
            element("label", attr("for", name) :: class_("form-label") :: Nil,
                text(label) :: Nil) ::
            element("input",
                attr("type", "date") :: id(name) :: attr("name", name) ::
                attr("value", value) :: class_(cls) :: Nil, Nil) ::
            (errors |> List.map(msg ->
                element("div", class_("invalid-feedback") :: Nil, text(msg) :: Nil))))

    /// 一覧の 1 行
    def bookingRow(c: Cargo): Html =
        let spec = BookingModel.routeSpecificationOf(c);
        element("tr", Nil,
            // 予約番号から詳細へ。**行のどこかを押せる**だけでは、
            // 押せることが利用者に伝わらない
            element("td", Nil,
                element("a", attr("href", "/bookings/${BookingModel.bookingIdOf(c)}") :: Nil,
                    text(BookingModel.bookingIdOf(c)) :: Nil) :: Nil) ::
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
    pub def index(cargos: List[Cargo], notice: Option[String]): Html =
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
            // 通知は見出しの前に置く（操作の結果は最初に目に入る位置にある必要がある）
            (match notice {
                case None    => empty()
                case Some(m) => alert("success", m)
             }) ::
            element("div", class_("d-flex justify-content-between align-items-center mb-3") :: Nil,
                element("h1", class_("h4 mb-0") :: Nil, text("貨物予約一覧") :: Nil) ::
                element("a", class_("btn btn-primary") ::
                             attr("href", "/bookings/new") :: Nil,
                    text("新規予約登録") :: Nil) :: Nil) ::
            body :: Nil)

    pub def renderNew(p: Option[Principal], view: BookingFormView,
                      locations: List[(String, String)], csrfToken: Option[String]): String =
        renderPage("貨物予約登録", p, "/bookings", csrfToken, newForm(view, locations, csrfToken))

    ///
    /// 貨物予約一覧。
    ///
    /// `notice` は PRG の遷移先で 1 回だけ出す通知（発行された予約番号など）。
    /// **文言は呼び出し側が組み立てて渡す**。
    ///
    pub def renderIndex(p: Option[Principal], cargos: List[Cargo],
                        csrfToken: Option[String], notice: Option[String]): String =
        renderPage("貨物予約一覧", p, "/bookings", csrfToken, index(cargos, notice))

    // ---- 予約詳細（/bookings/{bookingId}。US06） ----

    ///
    /// 予約詳細。
    ///
    /// 経路設計者へ引き渡す前に、営業担当者が内容を確認する画面である
    /// （US06 受入基準 1）。**引き渡せない場合は理由を示す**（受入基準 4）。
    ///
    pub def detail(c: Cargo, csrfToken: Option[String]): Html =
        let spec = BookingModel.routeSpecificationOf(c);
        element("div", class_("row") :: Nil,
            element("div", class_("col-md-8 offset-md-2") :: Nil,
                element("div", class_("d-flex justify-content-between align-items-center mb-3") :: Nil,
                    element("h1", class_("h4 mb-0") :: Nil,
                        text("予約 ${BookingModel.bookingIdOf(c)}") :: Nil) ::
                    element("span",
                        class_(BookingModel.statusBadgeClass(BookingModel.bookingStatusOf(c))) :: Nil,
                        text(BookingModel.statusLabel(BookingModel.bookingStatusOf(c))) :: Nil) :: Nil) ::
                element("table", class_("table") :: Nil,
                    element("tbody", Nil,
                        detailRow("出発地", BookingModel.originOf(spec)) ::
                        detailRow("仕向地", BookingModel.destinationOf(spec)) ::
                        detailRow("到着期限", BookingModel.arrivalDeadlineOf(spec)) ::
                        detailRow("貨物種別",
                                  BookingModel.cargoTypeLabel(BookingModel.cargoTypeOf(c))) ::
                        detailRow("重量",
                                  BookingModel.weightKilograms(BookingModel.weightGramsOf(c)) + " kg") ::
                        dimensionRow(c) :::
                        quantityRow(c) :::
                        descriptionRow(c) :::
                        hazardousRows(c) :::
                        temperatureRows(c)) :: Nil) ::
                assignSection(c, csrfToken) ::
                element("a", class_("btn btn-link") :: attr("href", "/bookings") :: Nil,
                    text("予約一覧へ戻る") :: Nil) :: Nil) :: Nil)

    def detailRow(label: String, value: String): Html =
        element("tr", Nil,
            element("th", class_("w-25") :: Nil, text(label) :: Nil) ::
            element("td", Nil, text(value) :: Nil) :: Nil)

    /// 任意項目は**値があるときだけ**行を出す（空行は情報を持たない）
    def dimensionRow(c: Cargo): List[Html] =
        match BookingModel.dimensionsOf(c) {
            case None => Nil
            case Some((l, w, h)) =>
                detailRow("寸法", "${millimetersToCm(l)} × ${millimetersToCm(w)} × ${millimetersToCm(h)} cm") :: Nil
        }

    /// ミリメートルの内部表現をセンチメートルの表示へ直す
    def millimetersToCm(mm: Int32): String =
        let whole = mm / 10;
        let fraction = mm - whole * 10;
        if (fraction == 0) "${whole}" else "${whole}.${fraction}"

    def quantityRow(c: Cargo): List[Html] =
        match BookingModel.quantityOf(c) {
            case None    => Nil
            case Some(q) => detailRow("個数", "${q}") :: Nil
        }

    def descriptionRow(c: Cargo): List[Html] =
        match BookingModel.descriptionOf(c) {
            case None    => Nil
            case Some(d) => detailRow("品名", d) :: Nil
        }

    def hazardousRows(c: Cargo): List[Html] =
        match BookingModel.hazardousDeclarationOf(c) {
            case None => Nil
            case Some(d) =>
                detailRow("危険物クラス", BookingModel.hazardousClassOf(d)) ::
                detailRow("UN 番号", BookingModel.unNumberOf(d)) ::
                detailRow("正式輸送品名", BookingModel.properShippingNameOf(d)) :: Nil
        }

    ///
    /// 温度条件の行。
    ///
    /// **内部表現（千分の一度）をそのまま出さない**。`-25500` と出ても
    /// 利用者には意味が分からない。
    ///
    def temperatureRows(c: Cargo): List[Html] =
        match BookingModel.temperatureRequirementOf(c) {
            case None => Nil
            case Some(t) =>
                let unit = match BookingModel.temperatureUnitOf(t) {
                    case BookingModel.TemperatureUnit.Celsius    => "℃"
                    case BookingModel.TemperatureUnit.Fahrenheit => "℉"
                };
                let lo = BookingModel.temperatureCelsiusText(BookingModel.minTemperatureMilliOf(t));
                let hi = BookingModel.temperatureCelsiusText(BookingModel.maxTemperatureMilliOf(t));
                detailRow("温度管理条件", "${lo} 〜 ${hi} ${unit}") :: Nil
        }

    ///
    /// 経路設計への引き渡し。
    ///
    /// 引き渡せない場合は**ボタンを出さず理由を示す**。押せるのに失敗する
    /// ボタンは、利用者に「壊れている」と受け取られる。
    ///
    def assignSection(c: Cargo, csrfToken: Option[String]): Html =
        match BookingModel.assignmentBlockedReason(c) {
            case Some(reason) => alert("warning", reason)
            case None =>
                form("/bookings/${BookingModel.bookingIdOf(c)}/assign-to-routing",
                     "post", csrfToken,
                    element("button", class_("btn btn-primary") ::
                                      attr("type", "submit") :: Nil,
                        text("経路設計へ引き渡す") :: Nil) :: Nil)
        }

    pub def renderDetail(p: Option[Principal], c: Cargo, csrfToken: Option[String]): String =
        renderPage("予約詳細", p, "/bookings", csrfToken, detail(c, csrfToken))

    ///
    /// 予約が見つからないときの画面。
    ///
    /// **一覧へ戻る導線を必ず置く**。行き止まりにすると、利用者は
    /// ブラウザの戻るしか手段がなくなる。
    ///
    pub def renderNotFound(p: Option[Principal], bookingId: String,
                           csrfToken: Option[String]): String =
        renderPage("予約詳細", p, "/bookings", csrfToken,
            element("div", class_("row") :: Nil,
                element("div", class_("col-md-8 offset-md-2") :: Nil,
                    alert("warning", "予約番号 ${bookingId} の予約は見つかりませんでした") ::
                    element("a", class_("btn btn-link") :: attr("href", "/bookings") :: Nil,
                        text("予約一覧へ戻る") :: Nil) :: Nil) :: Nil))

    pub def renderCargoTypeFields(view: BookingFormView): String =
        SharedHtml.render(cargoTypeFields(view))
}
