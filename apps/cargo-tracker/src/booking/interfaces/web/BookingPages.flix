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
    def bookingRow(row: (Cargo, Option[(String, String)])): Html =
        let (c, shipper) = row;
        let spec = BookingModel.routeSpecificationOf(c);
        element("tr", Nil,
            // 予約番号から詳細へ。**行のどこかを押せる**だけでは、
            // 押せることが利用者に伝わらない
            element("td", Nil,
                element("a", attr("href", "/bookings/${BookingModel.bookingIdOf(c)}") :: Nil,
                    text(BookingModel.bookingIdOf(c)) :: Nil) :: Nil) ::
            // 荷主名を出す。**予約番号だけでは誰の貨物か分からず**、
            // 「先週の件どうなってる？」の電話に応えられない（IT5 レビュー M15）
            element("td", Nil, text(shipperLabel(shipper)) :: Nil) ::
            element("td", Nil, text(BookingModel.originOf(spec)) :: Nil) ::
            element("td", Nil, text(BookingModel.destinationOf(spec)) :: Nil) ::
            element("td", Nil, text(BookingModel.arrivalDeadlineOf(spec)) :: Nil) ::
            element("td", Nil,
                text(BookingModel.cargoTypeLabel(BookingModel.cargoTypeOf(c))) :: Nil) ::
            element("td", Nil,
                text(weightKilograms(BookingModel.weightGramsOf(c)) + " kg") :: Nil) ::
            element("td", Nil,
                element("span",
                    class_(BookingModel.statusBadgeClass(BookingModel.bookingStatusOf(c))) :: Nil,
                    text(BookingModel.displayStatusOf(c)) :: Nil) :: Nil) :: Nil)

    /// 荷主の表示名（引けなかった場合も分かるようにする）
    def shipperLabel(shipper: Option[(String, String)]): String =
        match shipper {
            case Some((code, name)) => "${name}（${code}）"
            case None               => "取得できませんでした"
        }

    ///
    /// 貨物予約一覧。
    ///
    /// `canBook` は新規予約を登録できるロールか。**押せるのに 403 になる
    /// ボタンを出さない**（観点表の軸 3-7）。IT6 で荷主に一覧を開いたことで、
    /// 荷主が必ず見る画面に営業専用のボタンが出るようになっていた
    /// （IT6 レビュー H9。`VoyagePages` の `canManage` と同じ判断をここにも適用する）。
    ///
    pub def index(rows: List[(Cargo, Option[(String, String)])], notice: Option[String],
                  keyword: String, status: String, shipperName: String, canBook: Bool): Html =
        // **3 軸のいずれかが掛かっていれば「絞り込み中」**。語だけを見ていると、
        // 状態で絞って 0 件のときに「登録がありません」と出て、
        // 利用者は絞り込みを解除する発想に至らない
        let statusFilter = BookingModel.parseStatusFilter(status);
        let searching = String.trim(keyword) != "" or
                        statusFilter != BookingModel.StatusFilter.AnyStatus or
                        String.trim(shipperName) != "";
        let body =
            if (List.isEmpty(rows))
                // **「登録がない」と「条件に合わない」を区別する**（荷主一覧と同じ）
                element("p", class_("text-muted") :: Nil,
                    text(match statusFilter {
                             // **なぜ 0 件なのかを言う**。未知の状態語は 0 件に倒す
                             // （黙って全件へ広げない）。「予約が無い」と読まれると、
                             // 利用者は URL の綴りを疑わない
                             case BookingModel.StatusFilter.UnknownStatus(v) =>
                                 "「${v}」という予約状態はありません。上の欄から状態を選び直してください"
                             case _ =>
                         if (searching)
                             "該当する予約はありません。検索条件を変えてお試しください"
                         else if (canBook)
                             "登録されている貨物予約はありません。[新規予約登録] から登録してください"
                         else
                             // 荷主は自分で予約を登録できない。**次の行動を示す**
                             "ご登録されている貨物予約はありません。ご依頼は担当営業までご連絡ください"
                         }) :: Nil)
            else
                element("table", class_("table table-striped align-middle") :: Nil,
                    element("thead", Nil,
                        element("tr", Nil,
                            element("th", Nil, text("予約番号") :: Nil) ::
                            element("th", Nil, text("荷主") :: Nil) ::
                            element("th", Nil, text("出発地") :: Nil) ::
                            element("th", Nil, text("目的地") :: Nil) ::
                            element("th", Nil, text("到着期限") :: Nil) ::
                            element("th", Nil, text("貨物種別") :: Nil) ::
                            element("th", Nil, text("重量") :: Nil) ::
                            element("th", Nil, text("状態") :: Nil) :: Nil) :: Nil) ::
                    element("tbody", id("booking-list") :: Nil,
                        List.map(bookingRow, rows)) :: Nil);
        element("div", Nil,
            // 通知は見出しの前に置く（操作の結果は最初に目に入る位置にある必要がある）
            (match notice {
                case None    => empty()
                case Some(m) => alert("success", m)
             }) ::
            element("div", class_("d-flex justify-content-between align-items-center mb-3") :: Nil,
                element("h1", class_("h4 mb-0") :: Nil, text("貨物予約一覧") :: Nil) ::
                (if (canBook)
                    element("a", class_("btn btn-primary") ::
                                 attr("href", "/bookings/new") :: Nil,
                        text("新規予約登録") :: Nil) :: Nil
                 else Nil)) ::
            searchBox(keyword, status, shipperName, searching) ::
            body :: Nil)

    ///
    /// 絞り込みの入力欄。
    ///
    /// GET のフォームにする（検索結果を URL で共有・再訪できる。状態を変えないため
    /// CSRF トークンは付けない。arch-lint 規約 8）。荷主一覧と同じ形である。
    ///
    def searchBox(keyword: String, status: String, shipperName: String,
                  searching: Bool): Html =
        element("form", attr("method", "get") :: attr("action", "/bookings") ::
                        class_("row g-2 mb-3") :: Nil,
            element("div", class_("col-12 col-md flex-grow-1") :: Nil,
                element("input", attr("type", "text") :: attr("name", "q") ::
                                 attr("value", keyword) :: id("booking-search") ::
                                 attr("aria-label", "予約番号・出発地・仕向地") ::
                                 attr("placeholder", "予約番号・出発地・仕向地で絞り込む") ::
                                 class_("form-control") :: Nil, Nil) :: Nil) ::
            // 荷主名（IT6 レビュー M4）。荷主から電話が来たとき、
            // 営業が手元に持っているのは**社名**であって荷主コードではない
            element("div", class_("col-12 col-md-3") :: Nil,
                element("input", attr("type", "text") :: attr("name", "shipper") ::
                                 attr("value", shipperName) :: id("booking-shipper") ::
                                 attr("aria-label", "荷主名") ::
                                 attr("placeholder", "荷主名で絞り込む") ::
                                 class_("form-control") :: Nil, Nil) :: Nil) ::
            statusSelect(status) ::
            element("div", class_("col-auto") :: Nil,
                element("button", attr("type", "submit") :: class_("btn btn-outline-secondary") :: Nil,
                    text("絞り込む") :: Nil) :: Nil) ::
            (if (searching)
                 element("div", class_("col-auto") :: Nil,
                     element("a", attr("href", "/bookings") :: class_("btn btn-link") :: Nil,
                         text("解除") :: Nil) :: Nil)
             else
                 empty()) :: Nil)

    ///
    /// 状態の選択欄（IT7 レビュー M14）。
    ///
    /// `operation.md` の日次手順は「未着手（仮受付）の予約を朝に確認する」だが、
    /// 未着手が 20 件を超えると一覧を目で追うことになり手順が成立しない。
    /// **通知（US12）が将来リリースである以上、ここが唯一の安全網**である。
    ///
    /// 選択肢は [`BookingModel.allStatuses`](../../domain/model/Cargo.flix) から
    /// 組み立てる。ここに状態名を並べ直すと、状態を足したときに
    /// **絞り込みの選択肢からだけ漏れる**（そして誰も気付かない）。
    ///
    def statusSelect(selected: String): Html =
        let option = s -> {
            let value = BookingModel.statusToPersisted(s);
            element("option",
                attr("value", value) ::
                (if (value == String.trim(selected)) attr("selected", "selected") :: Nil else Nil),
                text(BookingModel.statusLabel(s)) :: Nil)
        };
        element("div", class_("col-12 col-md-auto") :: Nil,
            element("select", attr("name", "status") :: id("booking-status") ::
                              attr("aria-label", "予約状態") :: class_("form-select") :: Nil,
                // 既定は「すべての状態」。空文字は `parseStatusFilter` が
                // 「絞らない」に倒すため、選択肢と絞り込みの意味が一致する
                element("option", attr("value", "") :: Nil, text("すべての状態") :: Nil) ::
                List.map(option, BookingModel.allStatuses())) :: Nil)

    pub def renderNew(p: Option[Principal], view: BookingFormView,
                      locations: List[(String, String)], csrfToken: Option[String]): String =
        renderPage("貨物予約登録", p, "/bookings", csrfToken, newForm(view, locations, csrfToken))

    ///
    /// 貨物予約一覧。
    ///
    /// `notice` は PRG の遷移先で 1 回だけ出す通知（発行された予約番号など）。
    /// **文言は呼び出し側が組み立てて渡す**。
    ///
    pub def renderIndex(p: Option[Principal],
                        rows: List[(Cargo, Option[(String, String)])],
                        csrfToken: Option[String], notice: Option[String],
                        keyword: String, status: String, shipperName: String,
                        canBook: Bool): String =
        renderPage("貨物予約一覧", p, "/bookings", csrfToken,
                   index(rows, notice, keyword, status, shipperName, canBook))

    // ---- 予約詳細（/bookings/{bookingId}。US06） ----

    ///
    /// 予約詳細。
    ///
    /// 経路設計者へ引き渡す前に、営業担当者が内容を確認する画面である
    /// （US06 受入基準 1）。**引き渡せない場合は理由を示す**（受入基準 4）。
    ///
    pub def detail(c: Cargo, shipper: Option[(String, String)],
                   csrfToken: Option[String], canAssign: Bool, canRoute: Bool): Html =
        let spec = BookingModel.routeSpecificationOf(c);
        element("div", class_("row") :: Nil,
            element("div", class_("col-md-8 offset-md-2") :: Nil,
                element("div", class_("d-flex justify-content-between align-items-center mb-3") :: Nil,
                    element("h1", class_("h4 mb-0") :: Nil,
                        text("予約 ${BookingModel.bookingIdOf(c)}") :: Nil) ::
                    element("span",
                        class_(BookingModel.statusBadgeClass(BookingModel.bookingStatusOf(c))) :: Nil,
                        text(BookingModel.displayStatusOf(c)) :: Nil) :: Nil) ::
                element("table", class_("table") :: Nil,
                    element("tbody", Nil,
                        // **荷主を最初に出す**。引き渡し前の確認は
                        // 「誰の貨物か」から始まる（US06 受入基準 1）
                        shipperRow(shipper) :::
                        detailRow("出発地", BookingModel.originOf(spec)) ::
                        detailRow("仕向地", BookingModel.destinationOf(spec)) ::
                        detailRow("到着期限", BookingModel.arrivalDeadlineOf(spec)) ::
                        detailRow("貨物種別",
                                  BookingModel.cargoTypeLabel(BookingModel.cargoTypeOf(c))) ::
                        detailRow("重量",
                                  weightKilograms(BookingModel.weightGramsOf(c)) + " kg") ::
                        dimensionRow(c) :::
                        quantityRow(c) :::
                        descriptionRow(c) :::
                        hazardousRows(c) :::
                        temperatureRows(c)) :: Nil) ::
                itinerarySection(c) ::
                confirmLink(c, canAssign) ::
                assignSection(c, csrfToken, canAssign) ::
                routeSection(c, canRoute) ::
                element("a", class_("btn btn-link") :: attr("href", "/bookings") :: Nil,
                    text("予約一覧へ戻る") :: Nil) :: Nil) :: Nil)

    ///
    /// 経路割り当てへの導線（US08）。
    ///
    /// **経路設計者にだけ出す**。引き渡し済み（`ROUTE_PROPOSED`）でなければ
    /// 押しても対象外の画面になるため、状態でも絞る。
    /// **押せるのに何もできないボタンを出さない**（観点表の軸 3-7）。
    ///
    /// IT6 は「画面単体は正しいがロールをまたぐと切れる」欠陥を 3 件出した。
    /// 経路割り当ての画面を作っても、経路設計者がここへ辿り着けなければ
    /// 機能がないのと同じである。
    ///
    // ---- 確定確認（US13。IT9） ----

    ///
    /// 予約の確定・差し戻し・キャンセルを行う画面。
    ///
    /// **確認画面を経ずに操作できる導線は作らない**。荷主が承認したという
    /// 記録であり、押し間違いが取り返しのつかない操作になる。
    ///
    /// **3 つの操作を同じ画面に置く**。荷主の返事は 3 通り
    /// （承認・変更希望・見送り）であり、営業はどれか 1 つを選ぶ。
    /// 画面を分けると「断られたときどこへ行けばよいか」を覚える必要が出る。
    ///
    pub def confirmPage(c: Cargo, shipper: Option[(String, String)],
                        csrfToken: Option[String], message: Option[String]): Html =
        let spec = BookingModel.routeSpecificationOf(c);
        let bookingId = BookingModel.bookingIdOf(c);
        element("div", class_("row") :: Nil,
            element("div", class_("col-md-8 offset-md-2") :: Nil,
                (match message {
                    case None    => empty()
                    case Some(m) => alert("danger", m)
                 }) ::
                element("h1", class_("h4 mb-3") :: Nil,
                    text("予約の確定　${bookingId}") :: Nil) ::
                element("dl", class_("row border rounded p-3 mb-3") :: Nil,
                    confirmField("荷主", shipperLabel(shipper)) :::
                    confirmField("出発地", BookingModel.originOf(spec)) :::
                    confirmField("目的地", BookingModel.destinationOf(spec)) :::
                    confirmField("到着期限", BookingModel.arrivalDeadlineOf(spec)) :::
                    confirmField("状態", BookingModel.displayStatusOf(c))) ::
                // **選択されている経路を必ず出す**（受入基準 1）。
                // 荷主は経路を見て承認する
                itinerarySection(c) ::
                element("p", class_("text-muted") :: Nil,
                    text("荷主の承認を確認してから確定してください") :: Nil) ::
                form("/bookings/${bookingId}/confirm", "post", csrfToken,
                    element("button", attr("type", "submit") ::
                                      attr("name", "action") :: attr("value", "confirm") ::
                                      class_("btn btn-primary me-2") :: Nil,
                        text("確定する") :: Nil) ::
                    element("button", attr("type", "submit") ::
                                      attr("name", "action") :: attr("value", "sendBack") ::
                                      class_("btn btn-outline-secondary me-2") :: Nil,
                        text("経路設計へ差し戻す") :: Nil) ::
                    element("button", attr("type", "submit") ::
                                      attr("name", "action") :: attr("value", "cancel") ::
                                      class_("btn btn-outline-danger") :: Nil,
                        text("キャンセルする") :: Nil) :: Nil) ::
                element("a", class_("btn btn-link") ::
                             attr("href", "/bookings/${bookingId}") :: Nil,
                    text("予約詳細へ戻る") :: Nil) :: Nil) :: Nil)

    def confirmField(label: String, value: String): List[Html] =
        element("dt", class_("col-sm-3 text-muted") :: Nil, text(label) :: Nil) ::
        element("dd", class_("col-sm-9") :: Nil, text(value) :: Nil) :: Nil

    pub def renderConfirm(p: Option[Principal], c: Cargo,
                          shipper: Option[(String, String)],
                          csrfToken: Option[String], message: Option[String]): String =
        renderPage("予約の確定", p, "/bookings", csrfToken,
                   confirmPage(c, shipper, csrfToken, message))

    ///
    /// 割り当て済みの経路（US11 受入基準 1）。
    ///
    /// **確定経路と予約番号を同じ画面で確認できる**必要がある。
    /// 経路設計者が割り当てた後、営業が荷主へ提案するときに見る画面である。
    ///
    /// 割り当て前は何も出さない——空の表を出すと「経路が無い」のか
    /// 「読めなかった」のかが区別できない。
    ///
    def itinerarySection(c: Cargo): Html =
        match BookingModel.cargoItineraryOf(c) {
            case None    => empty()
            case Some(i) =>
                element("div", class_("mb-4") :: Nil,
                    element("h2", class_("h6 text-muted mb-2") :: Nil,
                        text("割り当て経路") :: Nil) ::
                    element("table", class_("table table-sm") :: id("cargo-itinerary") :: Nil,
                        element("thead", Nil,
                            element("tr", Nil,
                                element("th", Nil, text("航海番号") :: Nil) ::
                                element("th", Nil, text("積込") :: Nil) ::
                                element("th", Nil, text("荷降") :: Nil) ::
                                element("th", Nil, text("積込予定") :: Nil) ::
                                element("th", Nil, text("荷降予定") :: Nil) :: Nil) :: Nil) ::
                        element("tbody", Nil,
                            BookingModel.legsOf(i) |> List.map(legRow)) :: Nil) ::
                    element("p", class_("text-muted small") :: Nil,
                        text("到着予定: ${BookingModel.expectedArrivalTimeOf(i)}") :: Nil) :: Nil)
        }

    def legRow(l: BookingModel.Leg): Html =
        element("tr", Nil,
            element("td", Nil, text(BookingModel.legVoyageNumberOf(l)) :: Nil) ::
            element("td", Nil, text(BookingModel.legLoadLocationOf(l)) :: Nil) ::
            element("td", Nil, text(BookingModel.legUnloadLocationOf(l)) :: Nil) ::
            element("td", Nil, text(BookingModel.legLoadTimeOf(l)) :: Nil) ::
            element("td", Nil, text(BookingModel.legUnloadTimeOf(l)) :: Nil) :: Nil)

    ///
    /// 確定確認への導線（US13）。
    ///
    /// **経路が割り当てられてから出す**。経路が無いうちに出しても、
    /// 押した先で「経路がありません」と言われるだけである
    /// （押せるのに失敗するボタンを出さない）。
    ///
    /// 営業担当者のみ。荷主の承認を記録するのは営業の職掌である。
    ///
    def confirmLink(c: Cargo, canAssign: Bool): Html =
        let confirmable =
            BookingModel.bookingStatusOf(c) == BookingModel.BookingStatus.RouteProposed
            and BookingModel.isRouted(c);
        if (canAssign and confirmable)
            element("div", class_("mb-3") :: Nil,
                element("a", class_("btn btn-primary") ::
                             attr("href", "/bookings/${BookingModel.bookingIdOf(c)}/confirm") :: Nil,
                    text("予約を確定する") :: Nil) :: Nil)
        else
            empty()

    def routeSection(c: Cargo, canRoute: Bool): Html =
        let isProposed =
            BookingModel.bookingStatusOf(c) == BookingModel.BookingStatus.RouteProposed;
        if (canRoute and isProposed)
            element("div", class_("mb-3") :: Nil,
                element("a",
                    class_("btn btn-primary") ::
                    attr("href", "/bookings/${BookingModel.bookingIdOf(c)}/route") :: Nil,
                    text(if (BookingModel.isRouted(c)) "経路を選び直す" else "経路を割り当てる")
                        :: Nil) :: Nil)
        else
            empty()

    ///
    /// 荷主の行。
    ///
    /// 引けなかった場合も**行を出して分かるようにする**。黙って省くと、
    /// 「荷主が無い予約」なのか「読めなかった」のかを利用者が区別できない。
    ///
    def shipperRow(shipper: Option[(String, String)]): List[Html] =
        match shipper {
            case Some((code, name)) => detailRow("荷主", "${name}（${code}）") :: Nil
            case None               => detailRow("荷主", "取得できませんでした") :: Nil
        }

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

    // ---- 単位の表示（ADR-0006） ----
    //
    // **表示はドメインの責務ではない**。固定小数点整数から文字列への変換は
    // `SharedDecimal`（infrastructure）に集約しており、ドメインがそれを参照すると
    // 層の方向が逆流する（arch-lint 規約 1）。境界であるここに置く。

    /// ミリメートルの内部表現をセンチメートルの表示へ直す
    def millimetersToCm(mm: Int32): String =
        SharedDecimal.formatScaled(1, Int32.toInt64(mm))

    /// グラムの内部表現をキログラムの表示へ直す
    pub def weightKilograms(grams: Int64): String =
        SharedDecimal.formatScaled(3, grams)

    ///
    /// 千分の一度の内部表現を温度の表示へ直す。
    ///
    /// **単位を名前に含めない**。摂氏にも華氏にも使う。
    ///
    pub def temperatureText(milli: Int64): String =
        SharedDecimal.formatScaled(3, milli)

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
                let lo = temperatureText(BookingModel.minTemperatureMilliOf(t));
                let hi = temperatureText(BookingModel.maxTemperatureMilliOf(t));
                detailRow("温度管理条件", "${lo} 〜 ${hi} ${unit}") :: Nil
        }

    ///
    /// 経路設計への引き渡し。
    ///
    /// 引き渡せない場合は**ボタンを出さず理由を示す**。押せるのに失敗する
    /// ボタンは、利用者に「壊れている」と受け取られる。
    ///
    /// **状態だけでなくロールでも出し分ける**。引き渡しは営業の操作であり、
    /// 荷主が自分の予約を開いたときに出すと押して 403 になる
    /// （IT6 レビュー H9。状態の出し分けはあったがロールの出し分けが無かった）。
    ///
    def assignSection(c: Cargo, csrfToken: Option[String], canAssign: Bool): Html =
        if (not canAssign) empty()
        else match BookingModel.assignmentBlockedReason(c) {
            case Some(reason) => alert("warning", reason)
            case None =>
                form("/bookings/${BookingModel.bookingIdOf(c)}/assign-to-routing",
                     "post", csrfToken,
                    element("button", class_("btn btn-primary") ::
                                      attr("type", "submit") :: Nil,
                        text("経路設計へ引き渡す") :: Nil) :: Nil)
        }

    pub def renderDetail(p: Option[Principal], c: Cargo,
                         shipper: Option[(String, String)],
                         csrfToken: Option[String], canAssign: Bool,
                         canRoute: Bool): String =
        renderPage("予約詳細", p, "/bookings", csrfToken,
                   detail(c, shipper, csrfToken, canAssign, canRoute))

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
