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
          ShipperId: string
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
                    [ td [] [ str r.BookingId ]
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

        let cargoTypeOption v label =
            option
                (if values.CargoType = v then
                     [ _value v; _selected ]
                 else
                     [ _value v ])
                [ str label ]

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
                              [ cargoTypeOption "GENERAL" "一般"
                                cargoTypeOption "HAZARDOUS" "危険物"
                                cargoTypeOption "REFRIGERATED" "冷凍・冷蔵" ] ]
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
          ShipperId: string
          CargoType: string
          Origin: string
          Destination: string
          ArrivalDeadline: string
          Weight: string
          BookingStatus: string
          CanSubmitRouting: bool }

    /// 貨物予約詳細画面（`/bookings/{bookingId}`・US06）。
    /// 仮受付（Preliminary）のときのみ [経路設計を依頼] を表示する。
    let bookingDetail (roles: string list) (d: BookingDetail) (info: string option) : XmlNode =
        let row labelText value =
            tr [] [ th [ _class "w-25" ] [ str labelText ]; td [] [ str value ] ]

        let submitButton =
            if d.CanSubmitRouting then
                form
                    [ _method "post"
                      _action (sprintf "/bookings/%s/routing" d.BookingId)
                      _class "mt-3" ]
                    [ button [ _class "btn btn-primary"; _type "submit" ] [ str "経路設計を依頼" ] ]
            else
                emptyText

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
                          row "荷主 ID" d.ShipperId
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
              submitButton
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
