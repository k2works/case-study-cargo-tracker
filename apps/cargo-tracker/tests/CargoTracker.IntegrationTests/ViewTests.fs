module CargoTracker.IntegrationTests.ViewTests

open Xunit
open FsUnit.Xunit
open Giraffe.ViewEngine
open CargoTracker.Web

// タスク 2.5/2.6: ロール別ナビゲーションとビューの整合性を描画文字列で検証する。

let private render node = RenderView.AsString.htmlDocument node

[<Fact>]
let ``営業ロールのダッシュボードは荷主・見積を表示し管理設定は表示しない`` () =
    let html = render (Views.dashboard [ "ROLE_SALES" ])
    html |> should haveSubstring "荷主管理"
    html |> should haveSubstring "見積管理"
    html |> should not' (haveSubstring "管理設定")
    html |> should haveSubstring "ログアウト"

[<Fact>]
let ``管理者ロールのダッシュボードは管理設定を表示する`` () =
    let html = render (Views.dashboard [ "ROLE_ADMIN" ])
    html |> should haveSubstring "管理設定"

[<Fact>]
let ``未認証のログイン画面はログインメニューを表示しログアウトは表示しない`` () =
    let html = render (Views.login "" None)
    html |> should haveSubstring "ログイン"
    html |> should not' (haveSubstring "ログアウト")
    html |> should haveSubstring "パスワード"

[<Fact>]
let ``ログインエラーはメッセージと入力値を保持して表示する`` () =
    let html = render (Views.login "sales01" (Some "認証に失敗しました"))
    html |> should haveSubstring "認証に失敗しました"
    html |> should haveSubstring "sales01"

[<Fact>]
let ``経路設計ロールは航路管理を表示し荷主管理は表示しない`` () =
    let html = render (Views.dashboard [ "ROLE_ROUTE_DESIGNER" ])
    html |> should haveSubstring "航路管理"
    html |> should not' (haveSubstring "荷主管理")

[<Fact>]
let ``準備中プレースホルダは案内メッセージを表示する`` () =
    let html = render (Views.placeholder "貨物予約" [ "ROLE_SALES" ])
    html |> should haveSubstring "貨物予約"
    html |> should haveSubstring "準備中"
