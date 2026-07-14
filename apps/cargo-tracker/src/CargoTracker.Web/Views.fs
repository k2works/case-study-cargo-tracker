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
                a [ _class "nav-link"; _href "/logout" ] [ str "ログアウト" ]

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

    /// ログイン画面。エラー時はメッセージを表示し、入力値を保持する。
    let login (username: string) (error: string option) : XmlNode =
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
                                      input [ _class "form-control"; _id "username"; _name "username"; _value username ] ]
                                div
                                    [ _class "mb-3" ]
                                    [ label [ _class "form-label"; _for "password" ] [ str "パスワード" ]
                                      input
                                          [ _class "form-control"; _id "password"; _name "password"; _type "password" ] ]
                                button [ _class "btn btn-primary"; _type "submit" ] [ str "ログイン" ] ] ] ] ]
