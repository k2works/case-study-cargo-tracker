///
/// 画面共通の UI 部品（TS04）。
///
/// 画面数が増えたときにフォーム・バッジ・通知の書き方が各画面で分岐すると整合が崩れるため、
/// 部品として一元化する（docs/design/ui_design.md の共通レイアウト設計）。
///
/// `<form>` は本モジュールでのみ構築する。CSRF トークンの hidden フィールドを
/// 付け忘れる余地をなくすためであり、arch-lint の規約 8 で機械的に強制する
/// （docs/design/arch_lint_rules.md）。
///
/// すべて純粋関数のため、render 結果を直接検証できる。
///
mod SharedHtmlComponents {
    use SharedHtml.{element, text, fragment, empty, attr, class_, id}
    use SharedHtml.Html

    ///
    /// フォーム。
    ///
    /// 状態を変更しない GET フォームには CSRF トークンを付けない。
    /// トークンの生成・検証は IT3 の認証基盤で実装する。
    ///
    pub def form(action: String, method: String, csrfToken: Option[String], children: List[Html]): Html =
        let csrfField = match (String.toLowerCase(method), csrfToken) {
            case ("get", _)       => empty()
            case (_, Some(token)) => element("input",
                                        attr("type", "hidden") ::
                                        attr("name", "_csrf") ::
                                        attr("value", token) :: Nil, Nil)
            case (_, None)        => empty()
        };
        element("form",
            attr("action", action) :: attr("method", method) :: Nil,
            csrfField :: children)

    ///
    /// ラベル・入力欄・エラーメッセージの組。
    ///
    /// `for` と `id` を対応させ、エラー時は `aria-invalid` を付ける
    /// （docs/design/ui_design.md のアクセシビリティ要件）。
    ///
    pub def formField(name: String, label: String, value: String, errors: List[String]): Html =
        let hasError = not List.isEmpty(errors);
        let inputClass = if (hasError) "form-control is-invalid" else "form-control";
        let ariaInvalid = if (hasError) attr("aria-invalid", "true") :: Nil else Nil;
        let errorNodes =
            errors |> List.map(msg ->
                element("div", class_("invalid-feedback") :: Nil, text(msg) :: Nil));
        element("div", class_("mb-3") :: Nil,
            element("label", attr("for", name) :: class_("form-label") :: Nil,
                text(label) :: Nil) ::
            element("input",
                attr("type", "text") :: id(name) :: attr("name", name) ::
                attr("value", value) :: class_(inputClass) :: ariaInvalid, Nil) ::
            errorNodes)

    ///
    /// TransportStatus の表示ラベル。
    ///
    /// docs/design/ui_design.md の「TransportStatus バッジ定義」を正典とする。
    /// 未知の値は「不明」として扱い、永続化値が増えても表示を壊さない。
    /// `EXCEPTION` を「例外」ではなく「異常あり（要確認）」とするのは、荷主・荷受人にとって
    /// 「例外」が社内システムの語であり、取るべき行動が伝わらないためである。
    ///
    pub def statusLabel(status: String): String = match status {
        case "NOT_RECEIVED"    => "未受取"
        case "RECEIVED"        => "受取済"
        case "LOADED"          => "積み込み済"
        case "ONBOARD_CARRIER" => "輸送中"
        case "UNLOADED"        => "荷降ろし済"
        case "AWAITING_CLAIM"  => "引取待ち"
        case "CLAIMED"         => "引取完了"
        case "EXCEPTION"       => "異常あり（要確認）"
        case _                 => "不明"
    }

    /// バッジの Bootstrap クラス
    pub def statusBadgeClass(status: String): String = match status {
        case "NOT_RECEIVED"    => "badge bg-secondary"
        case "RECEIVED"        => "badge bg-info text-dark"
        case "LOADED"          => "badge bg-primary"
        case "ONBOARD_CARRIER" => "badge bg-primary"
        case "UNLOADED"        => "badge bg-warning text-dark"
        case "AWAITING_CLAIM"  => "badge bg-warning text-dark"
        case "CLAIMED"         => "badge bg-success"
        case "EXCEPTION"       => "badge bg-danger"
        case _                 => "badge bg-secondary"
    }

    /// TransportStatus のバッジ
    pub def statusBadge(status: String): Html =
        element("span", class_(statusBadgeClass(status)) :: Nil,
            text(statusLabel(status)) :: Nil)

    ///
    /// フラッシュメッセージ。
    ///
    /// `kind` は Bootstrap の種別（`success` / `danger` 等）。
    /// リダイレクト後に読み上げられるよう live region 属性を付ける。
    ///
    pub def alert(kind: String, message: String): Html =
        element("div",
            class_("alert alert-${kind}") ::
            attr("role", "status") ::
            attr("aria-live", "polite") :: Nil,
            text(message) :: Nil)
}
