namespace CargoTracker.Web

open Giraffe.ViewEngine

// Giraffe.ViewEngine による SSR ビュー（共通レイアウト・ナビゲーション・画面）。
// ロール別ナビゲーションは ui_design.md のマトリクスを正とする（ADR-0005）。

module Views =

    /// ナビゲーション項目（表示ラベル・URL・表示を許可するロール）。
    let private navMenu =
        [ "貨物予約", "/bookings", [ "ROLE_SALES"; "ROLE_SHIPPER" ]
          "荷主管理", "/shippers", [ "ROLE_SALES" ]
          "見積管理", "/estimates", [ "ROLE_SALES" ]
          "貨物追跡", "/tracking", [ "ROLE_SHIPPER"; "ROLE_CONSIGNEE"; "ROLE_TRACKER" ]
          "荷役管理", "/handling", [ "ROLE_HANDLER"; "ROLE_TRACKER" ]
          "航路管理", "/voyages", [ "ROLE_ROUTE_DESIGNER" ]
          "管理設定", "/admin/discount-policies", [ "ROLE_ADMIN" ] ]

    /// ロールに基づいてナビゲーションバーを描画する。未認証（roles 空）はログインのみ表示する。
    let private navbar (roles: string list) : XmlNode =
        let visibleItems =
            navMenu
            |> List.filter (fun (_, _, allowed) -> allowed |> List.exists (fun r -> List.contains r roles))
            |> List.map (fun (label, url, _) ->
                li [ _class "nav-item" ] [ a [ _class "nav-link"; _href url ] [ str label ] ])

        let authItem =
            if List.isEmpty roles then
                a [ _class "nav-link"; _href "/login" ] [ str "ログイン" ]
            else
                // ログアウトは状態変更操作のため POST フォームで送信する（CSRF 対策）。
                form
                    [ _method "post"; _action "/logout"; _class "d-inline" ]
                    [ button [ _class "btn btn-link nav-link"; _type "submit" ] [ str "ログアウト" ] ]

        nav
            [ _class "navbar navbar-expand-lg navbar-dark bg-dark" ]
            [ div
                  [ _class "container-fluid" ]
                  [ a [ _class "navbar-brand"; _href "/" ] [ b [] [ str "CargoTracker" ] ]
                    ul [ _class "navbar-nav me-auto" ] visibleItems
                    ul [ _class "navbar-nav" ] [ li [ _class "nav-item" ] [ authItem ] ] ] ]

    /// 共通レイアウト。Bootstrap 5 を CDN から読み込む。
    let layout (pageTitle: string) (roles: string list) (content: XmlNode list) : XmlNode =
        html
            [ _lang "ja" ]
            [ head
                  []
                  [ meta [ _charset "utf-8" ]
                    meta [ _name "viewport"; _content "width=device-width, initial-scale=1" ]
                    title [] [ str (sprintf "%s - CargoTracker" pageTitle) ]
                    link
                        [ _rel "stylesheet"
                          _href "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" ] ]
              body
                  []
                  [ navbar roles
                    main [ _class "container py-4" ] content
                    footer
                        [ _class "container text-center text-muted py-3" ]
                        [ str "Copyright (c) 2026 CargoTracker System" ] ] ]

    /// ダッシュボード（ホーム）。ロールに応じた導線カードを表示する。
    let dashboard (roles: string list) : XmlNode =
        let cards =
            navMenu
            |> List.filter (fun (_, _, allowed) -> allowed |> List.exists (fun r -> List.contains r roles))
            |> List.map (fun (label, url, _) ->
                div
                    [ _class "col-md-4 mb-3" ]
                    [ div
                          [ _class "card" ]
                          [ div
                                [ _class "card-body" ]
                                [ h5 [ _class "card-title" ] [ str label ]
                                  a [ _class "btn btn-primary"; _href url ] [ str "開く" ] ] ] ])

        layout "ホーム" roles [ h1 [ _class "mb-4" ] [ str "ダッシュボード" ]; div [ _class "row" ] cards ]

    /// 準備中プレースホルダ画面（ウォーキングスケルトンの骨格・後続 IT で実画面化）。
    let placeholder (pageTitle: string) (roles: string list) : XmlNode =
        layout
            pageTitle
            roles
            [ h1 [ _class "mb-4" ] [ str pageTitle ]
              div [ _class "alert alert-info" ] [ str "この機能は今後のイテレーションで実装予定です（準備中）。" ] ]

    /// 荷主一覧の表示行。
    type ShipperRow =
        { Code: string
          Name: string
          Email: string
          Kind: string
          DiscountRate: decimal }

    /// 荷主一覧画面（`/shippers`）。
    let shipperList (roles: string list) (rows: ShipperRow list) : XmlNode =
        let bodyRows =
            rows
            |> List.map (fun r ->
                tr
                    []
                    [ td [] [ str r.Code ]
                      td [] [ str r.Name ]
                      td [] [ str r.Email ]
                      td [] [ str r.Kind ]
                      td [] [ str (sprintf "%.1f%%" (r.DiscountRate * 100m)) ] ])

        layout
            "荷主管理"
            roles
            [ div
                  [ _class "d-flex justify-content-between align-items-center mb-4" ]
                  [ h1 [] [ str "荷主一覧" ]
                    a [ _class "btn btn-primary"; _href "/shippers/new" ] [ str "新規荷主登録" ] ]
              table
                  [ _class "table table-striped" ]
                  [ thead
                        []
                        [ tr
                              []
                              [ th [] [ str "荷主コード" ]
                                th [] [ str "名称" ]
                                th [] [ str "メール" ]
                                th [] [ str "種別" ]
                                th [] [ str "割引率" ] ] ]
                    tbody [] bodyRows ] ]

    /// 貨物予約一覧の表示行（US04）。
    type CargoRow =
        { BookingId: string
          ShipperName: string
          CargoType: string
          Origin: string
          Destination: string
          ArrivalDeadline: string
          BookingStatus: string }

    /// 予約状態の日本語表示。
    let bookingStatusLabel (status: string) : string =
        match status with
        | "PRELIMINARY" -> "仮受付"
        | "ROUTING_REQUESTED" -> "経路設計中"
        | "ROUTE_PROPOSED" -> "経路確定"
        | "CONFIRMED" -> "予約確定"
        | "CANCELLED" -> "キャンセル"
        | other -> other

    /// 貨物種別の日本語表示。
    let cargoTypeLabel (cargoType: string) : string =
        match cargoType with
        | "GENERAL" -> "一般"
        | "HAZARDOUS" -> "危険物"
        | "REFRIGERATED" -> "冷凍・冷蔵"
        | other -> other

    /// 貨物予約一覧画面（`/bookings`・US04）。
    let bookingList (roles: string list) (rows: CargoRow list) : XmlNode =
        let bodyRows =
            rows
            |> List.map (fun r ->
                tr
                    []
                    [ td [] [ a [ _href (sprintf "/bookings/%s" r.BookingId) ] [ str r.BookingId ] ]
                      td [] [ str r.ShipperName ]
                      td [] [ str (cargoTypeLabel r.CargoType) ]
                      td [] [ str r.Origin ]
                      td [] [ str r.Destination ]
                      td [] [ str r.ArrivalDeadline ]
                      td [] [ span [ _class "badge bg-secondary" ] [ str (bookingStatusLabel r.BookingStatus) ] ] ])

        layout
            "貨物予約"
            roles
            [ div
                  [ _class "d-flex justify-content-between align-items-center mb-4" ]
                  [ h1 [] [ str "貨物予約一覧" ]
                    a [ _class "btn btn-primary"; _href "/bookings/new" ] [ str "新規予約登録" ] ]
              table
                  [ _class "table table-striped" ]
                  [ thead
                        []
                        [ tr
                              []
                              [ th [] [ str "予約番号" ]
                                th [] [ str "荷主" ]
                                th [] [ str "種別" ]
                                th [] [ str "出発地" ]
                                th [] [ str "目的地" ]
                                th [] [ str "到着期限" ]
                                th [] [ str "状態" ] ] ]
                    tbody [] bodyRows ] ]

    /// 荷主選択肢（貨物予約フォーム）。
    type ShipperChoice = { Uuid: string; Label: string }

    /// 貨物予約登録フォームの入力値（エラー時の再表示に使う）。
    type BookingFormValues =
        { ShipperId: string
          OriginUnlocode: string
          DestinationUnlocode: string
          ArrivalDeadline: string
          CargoType: string
          WeightKg: string
          HazardClass: string
          UnNumber: string
          ProperShippingName: string
          MinTemperature: string
          MaxTemperature: string
          TemperatureUnit: string }

    let emptyBookingForm: BookingFormValues =
        { ShipperId = ""
          OriginUnlocode = ""
          DestinationUnlocode = ""
          ArrivalDeadline = ""
          CargoType = "GENERAL"
          WeightKg = ""
          HazardClass = ""
          UnNumber = ""
          ProperShippingName = ""
          MinTemperature = ""
          MaxTemperature = ""
          TemperatureUnit = "CELSIUS" }

    /// 貨物予約登録フォーム画面（`/bookings/new`・US04/US05）。
    /// 種別連動フィールドの表示は cargoType の選択に応じてクライアント側で切り替える
    /// （必須検証はサーバー側 BookCargo.book で担保する）。
    let bookingForm
        (roles: string list)
        (shippers: ShipperChoice list)
        (values: BookingFormValues)
        (error: string option)
        : XmlNode =
        let field labelText name value inputType =
            div
                [ _class "mb-3" ]
                [ label [ _class "form-label"; _for name ] [ str labelText ]
                  input [ _class "form-control"; _id name; _name name; _value value; _type inputType ] ]

        let shipperOptions =
            option [ _value "" ] [ str "-- 荷主を選択 --" ]
            :: (shippers
                |> List.map (fun s ->
                    option
                        (if s.Uuid = values.ShipperId then
                             [ _value s.Uuid; _selected ]
                         else
                             [ _value s.Uuid ])
                        [ str s.Label ]))

        // 種別コード→日本語ラベルは cargoTypeLabel を単一の真実として再利用する（DRY）。
        let cargoTypeOption v =
            option
                (if values.CargoType = v then
                     [ _value v; _selected ]
                 else
                     [ _value v ])
                [ str (cargoTypeLabel v) ]

        let unitOption v label =
            option
                (if values.TemperatureUnit = v then
                     [ _value v; _selected ]
                 else
                     [ _value v ])
                [ str label ]

        layout
            "貨物予約登録"
            roles
            [ h1 [ _class "mb-4" ] [ str "新規予約登録" ]
              (match error with
               | Some msg -> div [ _class "alert alert-danger" ] [ str msg ]
               | None -> emptyText)
              form
                  [ _method "post"; _action "/bookings" ]
                  [ div
                        [ _class "mb-3" ]
                        [ label [ _class "form-label"; _for "shipperId" ] [ str "荷主" ]
                          select [ _class "form-select"; _id "shipperId"; _name "shipperId" ] shipperOptions ]
                    field "出発地（UN/LOCODE）" "originUnlocode" values.OriginUnlocode "text"
                    field "目的地（UN/LOCODE）" "destinationUnlocode" values.DestinationUnlocode "text"
                    field "希望到着期限" "arrivalDeadline" values.ArrivalDeadline "date"
                    field "重量（kg）" "weightKg" values.WeightKg "number"
                    div
                        [ _class "mb-3" ]
                        [ label [ _class "form-label"; _for "cargoType" ] [ str "貨物種別" ]
                          select
                              [ _class "form-select"
                                _id "cargoType"
                                _name "cargoType"
                                attr "onchange" "toggleCargoFields()" ]
                              [ cargoTypeOption "GENERAL"
                                cargoTypeOption "HAZARDOUS"
                                cargoTypeOption "REFRIGERATED" ] ]
                    div
                        [ _id "hazardousFields" ]
                        [ field "危険物クラス" "hazardClass" values.HazardClass "text"
                          field "UN 番号" "unNumber" values.UnNumber "text"
                          field "正式輸送品名" "properShippingName" values.ProperShippingName "text" ]
                    div
                        [ _id "refrigeratedFields" ]
                        [ field "最低温度" "minTemperature" values.MinTemperature "number"
                          field "最高温度" "maxTemperature" values.MaxTemperature "number"
                          div
                              [ _class "mb-3" ]
                              [ label [ _class "form-label"; _for "temperatureUnit" ] [ str "温度単位" ]
                                select
                                    [ _class "form-select"; _id "temperatureUnit"; _name "temperatureUnit" ]
                                    [ unitOption "CELSIUS" "摂氏"; unitOption "FAHRENHEIT" "華氏" ] ] ]
                    button [ _class "btn btn-primary"; _type "submit" ] [ str "登録" ]
                    a [ _class "btn btn-secondary ms-2"; _href "/bookings" ] [ str "キャンセル" ] ]
              script
                  []
                  [ rawText
                        """
                        function toggleCargoFields() {
                          var t = document.getElementById('cargoType').value;
                          document.getElementById('hazardousFields').style.display = (t === 'HAZARDOUS') ? 'block' : 'none';
                          document.getElementById('refrigeratedFields').style.display = (t === 'REFRIGERATED') ? 'block' : 'none';
                        }
                        document.addEventListener('DOMContentLoaded', toggleCargoFields);
                        """ ] ]

    /// 貨物予約詳細の表示値（US06）。
    type BookingDetail =
        { BookingId: string
          ShipperName: string
          CargoType: string
          Origin: string
          Destination: string
          ArrivalDeadline: string
          Weight: string
          BookingStatus: string
          CanSubmitRouting: bool
          // US11/US13: 確定経路（旅程）と操作可否。
          Itinerary: string list
          CanConfirm: bool
          CanRestore: bool
          CanCancel: bool
          CanNotify: bool }

    /// 貨物予約詳細画面（`/bookings/{bookingId}`・US06/US11/US12/US13）。
    /// 仮受付（Preliminary）のときのみ [経路設計を依頼]、経路確定後は確定/差し戻し/キャンセルを表示する。
    let bookingDetail (roles: string list) (d: BookingDetail) (info: string option) : XmlNode =
        let row labelText value =
            tr [] [ th [ _class "w-25" ] [ str labelText ]; td [] [ str value ] ]

        let isSales = List.contains "ROLE_SALES" roles

        let actionForm path label btnClass =
            form
                [ _method "post"
                  _action (sprintf "/bookings/%s/%s" d.BookingId path)
                  _class "d-inline me-2" ]
                [ button [ _class (sprintf "btn %s" btnClass); _type "submit" ] [ str label ] ]

        let submitButton =
            if d.CanSubmitRouting then
                form
                    [ _method "post"
                      _action (sprintf "/bookings/%s/routing" d.BookingId)
                      _class "mt-3" ]
                    [ button [ _class "btn btn-primary"; _type "submit" ] [ str "経路設計を依頼" ] ]
            else
                emptyText

        // 確定経路（旅程）の表示。
        let itinerarySection =
            if List.isEmpty d.Itinerary then
                emptyText
            else
                div
                    [ _class "mt-4" ]
                    [ h2 [ _class "h5 mb-2" ] [ str "確定経路" ]
                      ul
                          [ _class "list-group" ]
                          (d.Itinerary |> List.map (fun s -> li [ _class "list-group-item" ] [ str s ])) ]

        // 経路確定後の操作（営業のみ）。
        let actionSection =
            if not isSales then
                emptyText
            else
                div
                    [ _class "mt-3" ]
                    [ (if d.CanNotify then
                           actionForm "notify" "荷主に経路を通知する" "btn-info"
                       else
                           emptyText)
                      (if d.CanConfirm then
                           actionForm "confirm" "予約を確定する" "btn-success"
                       else
                           emptyText)
                      (if d.CanRestore then
                           actionForm "restore" "経路設計へ差し戻す" "btn-warning"
                       else
                           emptyText)
                      (if d.CanCancel then
                           actionForm "cancel" "予約をキャンセルする" "btn-danger"
                       else
                           emptyText) ]

        layout
            "貨物予約"
            roles
            [ h1 [ _class "mb-4" ] [ str (sprintf "予約詳細 %s" d.BookingId) ]
              (match info with
               | Some msg -> div [ _class "alert alert-success" ] [ str msg ]
               | None -> emptyText)
              table
                  [ _class "table table-bordered" ]
                  [ tbody
                        []
                        [ row "予約番号" d.BookingId
                          row "荷主" d.ShipperName
                          row "貨物種別" (cargoTypeLabel d.CargoType)
                          row "出発地" d.Origin
                          row "目的地" d.Destination
                          row "到着期限" d.ArrivalDeadline
                          row "重量（kg）" d.Weight
                          tr
                              []
                              [ th [] [ str "状態" ]
                                td
                                    []
                                    [ span [ _class "badge bg-secondary" ] [ str (bookingStatusLabel d.BookingStatus) ] ] ] ] ]
              itinerarySection
              submitButton
              actionSection
              a [ _class "btn btn-secondary ms-2 mt-3"; _href "/bookings" ] [ str "一覧へ戻る" ] ]

    /// 荷主登録フォームの入力値（エラー時の再表示に使う）。
    type ShipperFormValues =
        { Name: string
          Email: string
          Phone: string
          Address: string
          IsCorporate: bool
          ContractNumber: string
          DiscountRatePercent: string }

    let emptyShipperForm =
        { Name = ""
          Email = ""
          Phone = ""
          Address = ""
          IsCorporate = false
          ContractNumber = ""
          DiscountRatePercent = "" }

    /// 荷主登録画面（`/shippers/new`）。個人/法人を切り替える（htmx なしのシンプル版）。
    let shipperForm (roles: string list) (values: ShipperFormValues) (error: string option) : XmlNode =
        let field labelText name value inputType =
            div
                [ _class "mb-3" ]
                [ label [ _class "form-label"; _for name ] [ str labelText ]
                  input [ _class "form-control"; _id name; _name name; _value value; _type inputType ] ]

        layout
            "荷主登録"
            roles
            [ h1 [ _class "mb-4" ] [ str "新規荷主登録" ]
              (match error with
               | Some msg -> div [ _class "alert alert-danger" ] [ str msg ]
               | None -> emptyText)
              form
                  [ _method "post"; _action "/shippers" ]
                  [ field "名称（氏名/社名）" "name" values.Name "text"
                    field "メールアドレス" "email" values.Email "email"
                    field "電話番号（任意）" "phone" values.Phone "text"
                    field "住所（任意）" "address" values.Address "text"
                    div
                        [ _class "form-check mb-3" ]
                        [ input (
                              [ _class "form-check-input"
                                _id "isCorporate"
                                _name "isCorporate"
                                _type "checkbox"
                                _value "true" ]
                              @ (if values.IsCorporate then [ _checked ] else [])
                          )
                          label [ _class "form-check-label"; _for "isCorporate" ] [ str "法人荷主" ] ]
                    field "契約番号（法人時）" "contractNumber" values.ContractNumber "text"
                    field "割引率 %（法人時・0〜30）" "discountRatePercent" values.DiscountRatePercent "number"
                    button [ _class "btn btn-primary"; _type "submit" ] [ str "登録" ]
                    a [ _class "btn btn-secondary ms-2"; _href "/shippers" ] [ str "キャンセル" ] ] ]

    /// 見積一覧の表示行。
    type EstimateRow =
        { EstimateId: string
          Origin: string
          Destination: string
          ArrivalDeadline: string
          CargoType: string
          WeightKg: decimal
          Status: string
          CandidateCount: int }

    /// 見積一覧画面（`/estimates`）。
    let estimateList (roles: string list) (rows: EstimateRow list) : XmlNode =
        let bodyRows =
            rows
            |> List.map (fun r ->
                tr
                    []
                    [ td [] [ str (r.EstimateId.Substring(0, 8)) ]
                      td [] [ str (sprintf "%s → %s" r.Origin r.Destination) ]
                      td [] [ str r.ArrivalDeadline ]
                      td [] [ str r.CargoType ]
                      td [] [ str (sprintf "%.1f kg" r.WeightKg) ]
                      td [] [ str (string r.CandidateCount) ]
                      td [] [ str r.Status ] ])

        layout
            "見積管理"
            roles
            [ div
                  [ _class "d-flex justify-content-between align-items-center mb-4" ]
                  [ h1 [] [ str "見積一覧" ]
                    a [ _class "btn btn-primary"; _href "/estimates/new" ] [ str "新規見積作成" ] ]
              table
                  [ _class "table table-striped" ]
                  [ thead
                        []
                        [ tr
                              []
                              [ th [] [ str "見積番号" ]
                                th [] [ str "区間" ]
                                th [] [ str "到着期限" ]
                                th [] [ str "貨物種別" ]
                                th [] [ str "重量" ]
                                th [] [ str "候補数" ]
                                th [] [ str "状態" ] ] ]
                    tbody [] bodyRows ] ]

    /// 見積作成フォームの入力値。
    type EstimateFormValues =
        { OriginUnlocode: string
          DestinationUnlocode: string
          ArrivalDeadline: string
          CargoType: string
          WeightKg: string }

    let emptyEstimateForm =
        { OriginUnlocode = ""
          DestinationUnlocode = ""
          ArrivalDeadline = ""
          CargoType = "GENERAL"
          WeightKg = "" }

    /// 見積作成画面（`/estimates/new`）。
    let estimateForm (roles: string list) (values: EstimateFormValues) (error: string option) : XmlNode =
        let field labelText name value inputType =
            div
                [ _class "mb-3" ]
                [ label [ _class "form-label"; _for name ] [ str labelText ]
                  input [ _class "form-control"; _id name; _name name; _value value; _type inputType ] ]

        let cargoOption v label =
            option
                (if values.CargoType = v then
                     [ _value v; _selected ]
                 else
                     [ _value v ])
                [ str label ]

        layout
            "見積作成"
            roles
            [ h1 [ _class "mb-4" ] [ str "新規見積作成" ]
              (match error with
               | Some msg -> div [ _class "alert alert-danger" ] [ str msg ]
               | None -> emptyText)
              form
                  [ _method "post"; _action "/estimates" ]
                  [ field "出発地（UN/LOCODE・例 JPTYO）" "originUnlocode" values.OriginUnlocode "text"
                    field "目的地（UN/LOCODE・例 USLAX）" "destinationUnlocode" values.DestinationUnlocode "text"
                    field "希望到着期限" "arrivalDeadline" values.ArrivalDeadline "date"
                    div
                        [ _class "mb-3" ]
                        [ label [ _class "form-label"; _for "cargoType" ] [ str "貨物種別" ]
                          select
                              [ _class "form-select"; _id "cargoType"; _name "cargoType" ]
                              [ cargoOption "GENERAL" "一般"
                                cargoOption "HAZARDOUS" "危険物"
                                cargoOption "REFRIGERATED" "冷凍・冷蔵" ] ]
                    field "重量 kg" "weightKg" values.WeightKg "number"
                    button [ _class "btn btn-primary"; _type "submit" ] [ str "見積作成" ]
                    a [ _class "btn btn-secondary ms-2"; _href "/estimates" ] [ str "キャンセル" ] ] ]

    /// ログイン画面。エラー時はメッセージを表示し、入力値を保持する。
    /// 初期表示ではシードの既定ユーザー ID・パスワードを事前入力する。
    /// selectableUsers が非空なら、ユーザー ID をセレクトボックスで選択できる（開発用シードユーザー）。
    let login (username: string) (password: string) (selectableUsers: string list) (error: string option) : XmlNode =
        let usernameField =
            if List.isEmpty selectableUsers then
                input [ _class "form-control"; _id "username"; _name "username"; _value username ]
            else
                select
                    [ _class "form-select"; _id "username"; _name "username" ]
                    (selectableUsers
                     |> List.map (fun u ->
                         option
                             (if u = username then
                                  [ _value u; _selected ]
                              else
                                  [ _value u ])
                             [ str u ]))

        layout
            "ログイン"
            []
            [ div
                  [ _class "row justify-content-center" ]
                  [ div
                        [ _class "col-md-5" ]
                        [ h1 [ _class "mb-4" ] [ str "ログイン" ]
                          (match error with
                           | Some msg -> div [ _class "alert alert-danger" ] [ str msg ]
                           | None -> emptyText)
                          form
                              [ _method "post"; _action "/login" ]
                              [ div
                                    [ _class "mb-3" ]
                                    [ label [ _class "form-label"; _for "username" ] [ str "ユーザー ID" ]
                                      usernameField ]
                                div
                                    [ _class "mb-3" ]
                                    [ label [ _class "form-label"; _for "password" ] [ str "パスワード" ]
                                      input
                                          [ _class "form-control"
                                            _id "password"
                                            _name "password"
                                            _type "password"
                                            _value password ] ]
                                button [ _class "btn btn-primary"; _type "submit" ] [ str "ログイン" ] ] ] ] ]

    // ---- Routing: 航路管理（US24/US25/US07）----

    /// 航路一覧の表示行。
    type VoyageRow =
        { VoyageNumber: string
          Vessel: string
          Carrier: string
          Origin: string
          Destination: string
          Departure: string
          Arrival: string }

    /// 航路一覧画面（`/voyages`・US07/US24）。
    let voyageList (roles: string list) (rows: VoyageRow list) : XmlNode =
        let bodyRows =
            rows
            |> List.map (fun r ->
                tr
                    []
                    [ td [] [ a [ _href (sprintf "/voyages/%s/edit" r.VoyageNumber) ] [ str r.VoyageNumber ] ]
                      td [] [ str r.Vessel ]
                      td [] [ str r.Carrier ]
                      td [] [ str r.Origin ]
                      td [] [ str r.Destination ]
                      td [] [ str r.Departure ]
                      td [] [ str r.Arrival ] ])

        layout
            "航路管理"
            roles
            [ div
                  [ _class "d-flex justify-content-between align-items-center mb-4" ]
                  [ h1 [] [ str "航路一覧" ]
                    a [ _class "btn btn-primary"; _href "/voyages/new" ] [ str "航海スケジュール登録" ] ]
              table
                  [ _class "table table-striped" ]
                  [ thead
                        []
                        [ tr
                              []
                              [ th [] [ str "航海番号" ]
                                th [] [ str "船名" ]
                                th [] [ str "運送会社" ]
                                th [] [ str "出発港" ]
                                th [] [ str "到着港" ]
                                th [] [ str "出発日" ]
                                th [] [ str "到着日" ] ] ]
                    tbody [] bodyRows ] ]

    /// 航海スケジュール登録／更新フォームの入力値。運送区間は最大 3 区間の固定行で受ける。
    type VoyageFormValues =
        { VoyageNumber: string
          VesselName: string
          CarrierName: string
          CargoGeneral: bool
          CargoHazardous: bool
          CargoRefrigerated: bool
          Legs: (string * string * string * string) list } // (出発港, 到着港, 出発日時, 到着日時)

    let emptyVoyageForm: VoyageFormValues =
        { VoyageNumber = ""
          VesselName = ""
          CarrierName = ""
          CargoGeneral = true
          CargoHazardous = false
          CargoRefrigerated = false
          Legs = [ ("", "", "", ""); ("", "", "", ""); ("", "", "", "") ] }

    /// 航海スケジュール登録／更新フォーム（`/voyages/new`・`/voyages/{n}/edit`・US24/US25）。
    let voyageForm
        (roles: string list)
        (pageTitle: string)
        (actionPath: string)
        (isEdit: bool)
        (values: VoyageFormValues)
        (error: string option)
        : XmlNode =
        let field labelText name value inputType =
            div
                [ _class "mb-3" ]
                [ label [ _class "form-label"; _for name ] [ str labelText ]
                  input [ _class "form-control"; _id name; _name name; _value value; _type inputType ] ]

        let checkbox name labelText isChecked =
            div
                [ _class "form-check" ]
                [ input (
                      [ _class "form-check-input"
                        _id name
                        _name name
                        _type "checkbox"
                        _value "true" ]
                      @ (if isChecked then [ _checked ] else [])
                  )
                  label [ _class "form-check-label"; _for name ] [ str labelText ] ]

        let legRow i (dep, arr, depDate, arrDate) =
            let n = i + 1

            div
                [ _class "row g-2 mb-2" ]
                [ div
                      [ _class "col" ]
                      [ input
                            [ _class "form-control"
                              _name (sprintf "leg%dDep" n)
                              _value dep
                              _placeholder "出発港(UN/LOCODE)" ] ]
                  div
                      [ _class "col" ]
                      [ input
                            [ _class "form-control"
                              _name (sprintf "leg%dArr" n)
                              _value arr
                              _placeholder "到着港(UN/LOCODE)" ] ]
                  div
                      [ _class "col" ]
                      [ input
                            [ _class "form-control"
                              _name (sprintf "leg%dDepDate" n)
                              _value depDate
                              _type "datetime-local" ] ]
                  div
                      [ _class "col" ]
                      [ input
                            [ _class "form-control"
                              _name (sprintf "leg%dArrDate" n)
                              _value arrDate
                              _type "datetime-local" ] ] ]

        layout
            "航路管理"
            roles
            [ h1 [ _class "mb-4" ] [ str pageTitle ]
              (match error with
               | Some msg -> div [ _class "alert alert-danger" ] [ str msg ]
               | None -> emptyText)
              form
                  [ _method "post"; _action actionPath ]
                  [ (if isEdit then
                         // 更新時は航海番号を変更不可（hidden で送出）。
                         div
                             [ _class "mb-3" ]
                             [ label [ _class "form-label" ] [ str "航海番号" ]
                               input [ _class "form-control"; _value values.VoyageNumber; _readonly ]
                               input [ _name "voyageNumber"; _type "hidden"; _value values.VoyageNumber ] ]
                     else
                         field "航海番号" "voyageNumber" values.VoyageNumber "text")
                    field "船名" "vesselName" values.VesselName "text"
                    field "運送会社" "carrierName" values.CarrierName "text"
                    div
                        [ _class "mb-3" ]
                        [ label [ _class "form-label" ] [ str "対応貨物種別" ]
                          checkbox "cargoGeneral" "一般" values.CargoGeneral
                          checkbox "cargoHazardous" "危険物" values.CargoHazardous
                          checkbox "cargoRefrigerated" "冷凍・冷蔵" values.CargoRefrigerated ]
                    label [ _class "form-label" ] [ str "運送区間（出発港・到着港・出発日時・到着日時／上から順序）" ]
                    div [ _class "form-text mb-2" ] [ str "※ 現在は最大 3 区間まで登録できます（4 区間以上の航路は今後対応予定）。" ]
                    div [] (values.Legs |> List.mapi legRow)
                    button [ _class "btn btn-primary mt-3"; _type "submit" ] [ str (if isEdit then "更新する" else "登録") ]
                    a [ _class "btn btn-secondary ms-2 mt-3"; _href "/voyages" ] [ str "キャンセル" ] ] ]

    /// 航海スケジュール更新の差分確認画面（US25 受入条件2）。変更前後を並べ、確定で初めて更新する。
    let voyageDiff
        (roles: string list)
        (voyageNumber: string)
        (oldValues: VoyageFormValues)
        (newValues: VoyageFormValues)
        : XmlNode =
        let cargoLabel (v: VoyageFormValues) =
            [ (v.CargoGeneral, "一般")
              (v.CargoHazardous, "危険物")
              (v.CargoRefrigerated, "冷凍・冷蔵") ]
            |> List.filter fst
            |> List.map snd
            |> String.concat ", "

        let legLabel (dep, arr, depDate, arrDate) =
            if dep = "" && arr = "" then
                ""
            else
                sprintf "%s → %s（%s 〜 %s）" dep arr depDate arrDate

        let legsLabel (v: VoyageFormValues) =
            v.Legs
            |> List.map legLabel
            |> List.filter (fun s -> s <> "")
            |> String.concat " / "

        // 変更前後が異なる行を強調する。
        let diffRow labelText oldV newV =
            let changed = oldV <> newV

            tr
                (if changed then [ _class "table-warning" ] else [])
                [ th [] [ str labelText ]; td [] [ str oldV ]; td [] [ str newV ] ]

        // 確定フォームに新しい値を hidden で載せる（POST /voyages/{n}/confirm）。
        let hidden name value =
            input [ _name name; _type "hidden"; _value value ]

        let legHiddenInputs =
            newValues.Legs
            |> List.mapi (fun i (dep, arr, depDate, arrDate) ->
                let n = i + 1

                [ hidden (sprintf "leg%dDep" n) dep
                  hidden (sprintf "leg%dArr" n) arr
                  hidden (sprintf "leg%dDepDate" n) depDate
                  hidden (sprintf "leg%dArrDate" n) arrDate ])
            |> List.concat

        let cargoHidden =
            [ if newValues.CargoGeneral then
                  hidden "cargoGeneral" "true"
              if newValues.CargoHazardous then
                  hidden "cargoHazardous" "true"
              if newValues.CargoRefrigerated then
                  hidden "cargoRefrigerated" "true" ]

        layout
            "航路管理"
            roles
            [ h1 [ _class "mb-4" ] [ str (sprintf "航海スケジュール更新の確認 %s" voyageNumber) ]
              div [ _class "alert alert-info" ] [ str "変更内容を確認してください。強調行が変更箇所です。" ]
              table
                  [ _class "table table-bordered" ]
                  [ thead [] [ tr [] [ th [] [ str "項目" ]; th [] [ str "変更前" ]; th [] [ str "変更後" ] ] ]
                    tbody
                        []
                        [ diffRow "船名" oldValues.VesselName newValues.VesselName
                          diffRow "運送会社" oldValues.CarrierName newValues.CarrierName
                          diffRow "対応貨物種別" (cargoLabel oldValues) (cargoLabel newValues)
                          diffRow "運送区間" (legsLabel oldValues) (legsLabel newValues) ] ]
              form
                  [ _method "post"; _action (sprintf "/voyages/%s/confirm" voyageNumber) ]
                  (hidden "voyageNumber" voyageNumber
                   :: hidden "vesselName" newValues.VesselName
                   :: hidden "carrierName" newValues.CarrierName
                   :: (cargoHidden
                       @ legHiddenInputs
                       @ [ button [ _class "btn btn-primary"; _type "submit" ] [ str "更新する" ]
                           a [ _class "btn btn-secondary ms-2"; _href "/voyages" ] [ str "キャンセル" ] ])) ]

    // ---- Routing: 経路設計依頼一覧・経路設計（US07/US08）----

    /// 経路設計依頼一覧の表示行（経路設計中の予約）。
    type RoutingRequestRow =
        { BookingId: string
          CargoType: string
          Origin: string
          Destination: string
          ArrivalDeadline: string }

    /// 経路設計依頼一覧画面（`/routing/requests`・US07）。経路設計中（RoutingRequested）の予約を表示する。
    let routingRequestList (roles: string list) (rows: RoutingRequestRow list) : XmlNode =
        let bodyRows =
            rows
            |> List.map (fun r ->
                tr
                    []
                    [ td [] [ a [ _href (sprintf "/routing/requests/%s" r.BookingId) ] [ str r.BookingId ] ]
                      td [] [ str (cargoTypeLabel r.CargoType) ]
                      td [] [ str r.Origin ]
                      td [] [ str r.Destination ]
                      td [] [ str r.ArrivalDeadline ] ])

        layout
            "経路設計依頼"
            roles
            [ h1 [ _class "mb-4" ] [ str "経路設計依頼一覧" ]
              (if List.isEmpty rows then
                   div [ _class "alert alert-info" ] [ str "経路設計待ちの予約はありません。" ]
               else
                   table
                       [ _class "table table-striped" ]
                       [ thead
                             []
                             [ tr
                                   []
                                   [ th [] [ str "予約番号" ]
                                     th [] [ str "種別" ]
                                     th [] [ str "出発地" ]
                                     th [] [ str "目的地" ]
                                     th [] [ str "到着期限" ] ] ]
                         tbody [] bodyRows ]) ]

    /// 経路候補の表示行（US08）。
    type RouteCandidateRow =
        { Index: int
          VoyageNumbers: string
          TransitPorts: string
          TransitDays: int
          EstimatedCost: string
          IsDirect: bool }

    /// 経路設計・候補算出画面（`/routing/requests/{bookingId}`・US07/US08）。
    let routingDesign
        (roles: string list)
        (bookingId: string)
        (origin: string)
        (destination: string)
        (deadline: string)
        (candidates: RouteCandidateRow list)
        : XmlNode =
        let candidateRows =
            candidates
            |> List.map (fun c ->
                tr
                    []
                    [ td
                          []
                          [ str c.VoyageNumbers
                            (if c.IsDirect then
                                 span [ _class "badge bg-success ms-2" ] [ str "直行" ]
                             else
                                 emptyText) ]
                      td [] [ str (if c.TransitPorts = "" then "-" else c.TransitPorts) ]
                      td [] [ str (sprintf "%d 日" c.TransitDays) ]
                      td [] [ str c.EstimatedCost ]
                      td
                          []
                          [ form
                                [ _method "post"; _action (sprintf "/routing/requests/%s/propose" bookingId) ]
                                [ input [ _type "hidden"; _name "candidateIndex"; _value (string c.Index) ]
                                  // US10: 調整した期限で算出した候補を確定する場合、同じ期限を引き継ぐ。
                                  input [ _type "hidden"; _name "deadline"; _value deadline ]
                                  button [ _type "submit"; _class "btn btn-sm btn-primary" ] [ str "この経路で確定" ] ] ] ])

        layout
            "経路設計"
            roles
            [ h1 [ _class "mb-4" ] [ str (sprintf "経路設計 %s" bookingId) ]
              table
                  [ _class "table table-bordered mb-4" ]
                  [ tbody
                        []
                        [ tr [] [ th [ _class "w-25" ] [ str "出発地" ]; td [] [ str origin ] ]
                          tr [] [ th [] [ str "目的地" ]; td [] [ str destination ] ]
                          tr [] [ th [] [ str "到着期限（調整可）" ]; td [] [ str deadline ] ] ] ]
              // US10: 期限調整・再算出。営業と条件を協議のうえ到着期限を調整して候補を再算出する。
              form
                  [ _method "get"
                    _action (sprintf "/routing/requests/%s" bookingId)
                    _class "row g-2 align-items-end mb-4" ]
                  [ div
                        [ _class "col-auto" ]
                        [ label [ _class "form-label"; _for "deadline" ] [ str "到着期限を調整して再算出" ]
                          input
                              [ _class "form-control"
                                _type "date"
                                _id "deadline"
                                _name "deadline"
                                _value deadline ] ]
                    div
                        [ _class "col-auto" ]
                        [ button [ _type "submit"; _class "btn btn-outline-primary" ] [ str "この期限で再算出" ] ] ]
              div [ _class "form-text mb-3" ] [ str "※ 期限緩和は営業を通じて荷主と条件を協議のうえ行ってください（US10）。" ]
              h2 [ _class "h4 mb-3" ] [ str "経路候補（直行優先・所要日数昇順）" ]
              (if List.isEmpty candidates then
                   div [ _class "alert alert-warning" ] [ str "期限内に到達可能な経路候補がありません。到着期限の緩和や航海スケジュールの追加を検討してください。" ]
               else
                   table
                       [ _class "table table-striped" ]
                       [ thead
                             []
                             [ tr
                                   []
                                   [ th [] [ str "航海番号" ]
                                     th [] [ str "経由港" ]
                                     th [] [ str "所要日数" ]
                                     th [] [ str "概算費用（暫定）" ]
                                     th [] [ str "操作" ] ] ]
                         tbody [] candidateRows ])
              (if List.isEmpty candidates then
                   emptyText
               else
                   div [ _class "form-text mt-2" ] [ str "※ 概算費用は暫定値です。正式な料金は精算フェーズで確定します。" ])
              a [ _class "btn btn-secondary mt-3"; _href "/routing/requests" ] [ str "依頼一覧へ戻る" ] ]

    /// 輸送状態の日本語表示（TransportStatus の toString 値）。
    let transportStatusLabel (status: string) : string =
        match status with
        | "NOT_RECEIVED" -> "受領待ち"
        | "RECEIVED" -> "受領済"
        | "LOADED" -> "積込済"
        | "ONBOARD_CARRIER" -> "輸送中"
        | "UNLOADED" -> "荷降し済"
        | "AWAITING_CLAIM" -> "引取待ち"
        | "CLAIMED" -> "引取済"
        | "IN_EXCEPTION" -> "例外発生"
        | other -> other

    /// 追跡イベント種別の日本語表示。
    let trackingEventLabel (eventType: string) : string =
        match eventType with
        | "RECEIVED" -> "受領"
        | "LOADED" -> "積込"
        | "DEPARTED" -> "出港"
        | "UNLOADED" -> "荷降し"
        | "ARRIVED" -> "入港"
        | "CLAIMED" -> "引取"
        | other -> other

    /// 追跡イベントの表示行（US18）。
    type TrackingEventRow =
        { EventType: string
          Location: string
          EventTime: string }

    /// 追跡詳細の表示値（US18）。
    /// 追跡詳細の例外表示行（US19/US20）。Index は解決アクションの対象指定に使う。
    type TrackingExceptionRow =
        { Index: int
          ExceptionType: string
          Location: string
          OccurredAt: string
          Description: string
          Escalated: bool
          Resolved: bool }

    type TrackingDetailView =
        { TrackingNumber: string
          TransportStatus: string
          Events: TrackingEventRow list
          Exceptions: TrackingExceptionRow list }

    /// 追跡番号入力画面（`/tracking`・US18）。
    let trackingInput (roles: string list) (error: string option) : XmlNode =
        layout
            "貨物追跡"
            roles
            [ h1 [ _class "mb-4" ] [ str "貨物追跡" ]
              (match error with
               | Some msg -> div [ _class "alert alert-warning" ] [ str msg ]
               | None -> emptyText)
              form
                  [ _method "get"; _action "/tracking/search"; _class "row g-2" ]
                  [ div
                        [ _class "col-auto" ]
                        [ input
                              [ _class "form-control"
                                _type "text"
                                _name "trackingNumber"
                                _placeholder "追跡番号（例: TRK-XXXXXXXX）" ] ]
                    div [ _class "col-auto" ] [ button [ _type "submit"; _class "btn btn-primary" ] [ str "照会" ] ] ] ]

    /// 追跡詳細のタイムライン本体（認証あり・公開ページ共通）。
    let private trackingTimeline (d: TrackingDetailView) : XmlNode list =
        [ h1 [ _class "mb-3" ] [ str (sprintf "追跡 %s" d.TrackingNumber) ]
          div
              [ _class "mb-4" ]
              [ span [ _class "badge bg-primary fs-6" ] [ str (transportStatusLabel d.TransportStatus) ] ]
          h2 [ _class "h5 mb-2" ] [ str "追跡イベント履歴" ]
          (if List.isEmpty d.Events then
               div [ _class "alert alert-info" ] [ str "まだ追跡イベントはありません。" ]
           else
               table
                   [ _class "table table-striped" ]
                   [ thead [] [ tr [] [ th [] [ str "日時" ]; th [] [ str "作業種別" ]; th [] [ str "場所" ] ] ]
                     tbody
                         []
                         (d.Events
                          |> List.map (fun e ->
                              tr
                                  []
                                  [ td [] [ str e.EventTime ]
                                    td [] [ str (trackingEventLabel e.EventType) ]
                                    td [] [ str e.Location ] ])) ]) ]

    /// 例外種別の日本語表示。
    let exceptionTypeLabel (exType: string) : string =
        match exType with
        | "DELAY" -> "遅延"
        | "DAMAGE" -> "破損"
        | "LOST" -> "紛失"
        | "CUSTOMS_HOLD" -> "通関保留"
        | other -> other

    /// 例外一覧と解決導線（US19/US20・ROLE_TRACKER のみ操作可能）。
    let private exceptionSection (roles: string list) (d: TrackingDetailView) : XmlNode list =
        let isTracker = List.contains "ROLE_TRACKER" roles

        [ div
              [ _class "d-flex justify-content-between align-items-center mt-4 mb-2" ]
              [ h2 [ _class "h5 mb-0" ] [ str "例外" ]
                (if isTracker then
                     a
                         [ _class "btn btn-outline-danger btn-sm"
                           _href (sprintf "/tracking/%s/exceptions/new" d.TrackingNumber) ]
                         [ str "例外を登録" ]
                 else
                     emptyText) ]
          (if List.isEmpty d.Exceptions then
               div [ _class "alert alert-info" ] [ str "例外は登録されていません。" ]
           else
               table
                   [ _class "table table-bordered" ]
                   [ thead
                         []
                         [ tr
                               []
                               [ th [] [ str "種別" ]
                                 th [] [ str "発生日時" ]
                                 th [] [ str "場所" ]
                                 th [] [ str "状況・対応方針" ]
                                 th [] [ str "状態" ]
                                 th [] [ str "操作" ] ] ]
                     tbody
                         []
                         (d.Exceptions
                          |> List.map (fun x ->
                              tr
                                  []
                                  [ td [] [ str (exceptionTypeLabel x.ExceptionType) ]
                                    td [] [ str x.OccurredAt ]
                                    td [] [ str x.Location ]
                                    td [] [ str x.Description ]
                                    td
                                        []
                                        [ if x.Resolved then
                                              span [ _class "badge bg-success" ] [ str "解決済み" ]
                                          else
                                              span [ _class "badge bg-danger" ] [ str "未解決" ]
                                          if x.Escalated && not x.Resolved then
                                              span [ _class "badge bg-warning text-dark ms-1" ] [ str "エスカレーション" ] ]
                                    td
                                        []
                                        [ if isTracker && not x.Resolved then
                                              form
                                                  [ _method "post"
                                                    _action (
                                                        sprintf
                                                            "/tracking/%s/exceptions/%d/resolve"
                                                            d.TrackingNumber
                                                            x.Index
                                                    ) ]
                                                  [ input
                                                        [ _type "text"
                                                          _name "resolutionNote"
                                                          _class "form-control form-control-sm mb-1"
                                                          _placeholder "対応内容" ]
                                                    button
                                                        [ _type "submit"; _class "btn btn-sm btn-success" ]
                                                        [ str "解決" ] ]
                                          else
                                              emptyText ] ])) ]) ]

    /// 追跡詳細画面（`/tracking/{trackingNumber}`・US18/US19/US20・認証あり）。
    let trackingDetail (roles: string list) (d: TrackingDetailView) : XmlNode =
        layout
            "追跡詳細"
            roles
            (trackingTimeline d
             @ exceptionSection roles d
             @ [ a [ _class "btn btn-secondary mt-3"; _href "/tracking" ] [ str "追跡入力へ戻る" ] ])

    /// 公開追跡ページ（`/public/tracking/{accessToken}`・US18・未認証）。
    let publicTracking (d: TrackingDetailView) : XmlNode = layout "公開追跡" [] (trackingTimeline d)

    /// 荷役履歴の表示行（US15）。
    type HandlingRow =
        { BookingId: string
          HandlingType: string
          Location: string
          CompletionTime: string
          VoyageNumber: string }

    /// 荷役種別の日本語表示。
    let handlingTypeLabel (handlingType: string) : string =
        match handlingType with
        | "RECEIVE" -> "受領"
        | "LOAD" -> "積込"
        | "UNLOAD" -> "荷降し"
        | "CUSTOMS" -> "通関"
        | "CLAIM" -> "引取"
        | other -> other

    /// 荷役作業一覧画面（`/handling`・US15）。
    let handlingList (roles: string list) (msg: string option) (rows: HandlingRow list) : XmlNode =
        let banner =
            match msg with
            | Some "handling_ok" -> div [ _class "alert alert-success" ] [ str "荷役作業を登録しました。" ]
            | Some "handling_warning" -> div [ _class "alert alert-warning" ] [ str "荷役作業を登録しましたが、作業場所が予定と一致しません（警告）。" ]
            | Some "handling_misrouted" ->
                div [ _class "alert alert-danger" ] [ str "荷役作業を登録しましたが、予定ルート外です（Misrouted）。経路を確認してください。" ]
            | _ -> emptyText

        let bodyRows =
            rows
            |> List.map (fun r ->
                tr
                    []
                    [ td [] [ str r.BookingId ]
                      td [] [ str (handlingTypeLabel r.HandlingType) ]
                      td [] [ str r.Location ]
                      td [] [ str r.CompletionTime ]
                      td [] [ str r.VoyageNumber ] ])

        layout
            "荷役管理"
            roles
            [ banner
              div
                  [ _class "d-flex justify-content-between align-items-center mb-4" ]
                  [ h1 [] [ str "荷役作業一覧" ]
                    a [ _class "btn btn-primary"; _href "/handling/new" ] [ str "荷役作業を登録" ] ]
              (if List.isEmpty rows then
                   div [ _class "alert alert-info" ] [ str "荷役作業の記録はまだありません。" ]
               else
                   table
                       [ _class "table table-striped" ]
                       [ thead
                             []
                             [ tr
                                   []
                                   [ th [] [ str "予約番号" ]
                                     th [] [ str "作業種別" ]
                                     th [] [ str "場所" ]
                                     th [] [ str "完了日時" ]
                                     th [] [ str "航海番号" ] ] ]
                         tbody [] bodyRows ]) ]

    /// 荷役登録フォーム（`/handling/new`・US15/US16）。引取は荷受人確認欄を表示する。
    let handlingForm (roles: string list) (info: string option) (error: string option) : XmlNode =
        layout
            "荷役作業登録"
            roles
            [ h1 [ _class "mb-4" ] [ str "荷役作業登録" ]
              (match info with
               | Some msg -> div [ _class "alert alert-success" ] [ str msg ]
               | None -> emptyText)
              (match error with
               | Some msg -> div [ _class "alert alert-warning" ] [ str msg ]
               | None -> emptyText)
              form
                  [ _method "post"; _action "/handling" ]
                  [ div
                        [ _class "mb-3" ]
                        [ label [ _class "form-label"; _for "trackingNumber" ] [ str "追跡番号" ]
                          input
                              [ _class "form-control"
                                _id "trackingNumber"
                                _name "trackingNumber"
                                _type "text" ] ]
                    div
                        [ _class "mb-3" ]
                        [ label [ _class "form-label"; _for "handlingType" ] [ str "作業種別" ]
                          select
                              [ _class "form-select"; _id "handlingType"; _name "handlingType" ]
                              [ option [ _value "RECEIVE" ] [ str "受領" ]
                                option [ _value "LOAD" ] [ str "積込" ]
                                option [ _value "UNLOAD" ] [ str "荷降し" ]
                                option [ _value "CLAIM" ] [ str "引取" ] ] ]
                    div
                        [ _class "mb-3" ]
                        [ label [ _class "form-label"; _for "location" ] [ str "作業場所（UN/LOCODE）" ]
                          input [ _class "form-control"; _id "location"; _name "location"; _type "text" ] ]
                    div
                        [ _class "mb-3" ]
                        [ label [ _class "form-label"; _for "voyageNumber" ] [ str "航海番号（積込/荷降し時）" ]
                          input
                              [ _class "form-control"
                                _id "voyageNumber"
                                _name "voyageNumber"
                                _type "text" ] ]
                    div
                        [ _class "mb-3" ]
                        [ label [ _class "form-label"; _for "consigneeConfirmation" ] [ str "荷受人確認（引取時・署名または確認コード）" ]
                          input
                              [ _class "form-control"
                                _id "consigneeConfirmation"
                                _name "consigneeConfirmation"
                                _type "text" ] ]
                    button [ _type "submit"; _class "btn btn-primary" ] [ str "登録" ]
                    a [ _class "btn btn-secondary ms-2"; _href "/handling" ] [ str "一覧へ戻る" ] ] ]

    /// 貨物状態手動更新フォーム（`/tracking/{trackingNumber}/status/new`・US17）。
    /// 例外登録フォーム（`/tracking/{trackingNumber}/exceptions/new`・US19/US20・ROLE_TRACKER）。
    let exceptionForm (roles: string list) (trackingNumber: string) (error: string option) : XmlNode =
        layout
            "例外登録"
            roles
            [ h1 [ _class "mb-4" ] [ str (sprintf "例外登録 %s" trackingNumber) ]
              (match error with
               | Some msg -> div [ _class "alert alert-warning" ] [ str msg ]
               | None -> emptyText)
              div [ _class "alert alert-danger" ] [ str "例外種別「紛失（LOST）」を選択した場合、管理職へのエスカレーション通知が必須として自動送信されます。" ]
              form
                  [ _method "post"
                    _action (sprintf "/tracking/%s/exceptions/new" trackingNumber) ]
                  [ div
                        [ _class "mb-3" ]
                        [ label [ _class "form-label"; _for "exceptionType" ] [ str "例外種別" ]
                          select
                              [ _class "form-select"; _id "exceptionType"; _name "exceptionType" ]
                              [ option [ _value "DELAY" ] [ str "遅延（DELAY）" ]
                                option [ _value "DAMAGE" ] [ str "破損（DAMAGE）" ]
                                option [ _value "LOST" ] [ str "紛失（LOST）" ]
                                option [ _value "CUSTOMS_HOLD" ] [ str "通関保留（CUSTOMS_HOLD）" ] ] ]
                    div
                        [ _class "mb-3" ]
                        [ label [ _class "form-label"; _for "location" ] [ str "発生場所（UN/LOCODE）" ]
                          input [ _class "form-control"; _id "location"; _name "location"; _type "text" ] ]
                    div
                        [ _class "mb-3" ]
                        [ label [ _class "form-label"; _for "description" ] [ str "状況説明・対応方針" ]
                          textarea [ _class "form-control"; _id "description"; _name "description"; _rows "3" ] [] ]
                    button [ _type "submit"; _class "btn btn-primary" ] [ str "登録" ]
                    a
                        [ _class "btn btn-secondary ms-2"
                          _href (sprintf "/tracking/%s" trackingNumber) ]
                        [ str "追跡詳細へ戻る" ] ] ]

    let manualStatusForm (roles: string list) (trackingNumber: string) (error: string option) : XmlNode =
        layout
            "貨物状態更新"
            roles
            [ h1 [ _class "mb-4" ] [ str (sprintf "貨物状態更新 %s" trackingNumber) ]
              (match error with
               | Some msg -> div [ _class "alert alert-warning" ] [ str msg ]
               | None -> emptyText)
              form
                  [ _method "post"; _action (sprintf "/tracking/%s/status" trackingNumber) ]
                  [ div
                        [ _class "mb-3" ]
                        [ label [ _class "form-label"; _for "eventType" ] [ str "状態変更" ]
                          select
                              [ _class "form-select"; _id "eventType"; _name "eventType" ]
                              [ option [ _value "DEPARTED" ] [ str "出港（輸送中へ）" ]
                                option [ _value "ARRIVED" ] [ str "入港（引取待ちへ）" ] ] ]
                    div
                        [ _class "mb-3" ]
                        [ label [ _class "form-label"; _for "location" ] [ str "場所（UN/LOCODE）" ]
                          input [ _class "form-control"; _id "location"; _name "location"; _type "text" ] ]
                    button [ _type "submit"; _class "btn btn-primary" ] [ str "更新" ]
                    a
                        [ _class "btn btn-secondary ms-2"
                          _href (sprintf "/tracking/%s" trackingNumber) ]
                        [ str "追跡詳細へ戻る" ] ] ]
