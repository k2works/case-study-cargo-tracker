// 適法: Html モジュール内での RawUnsafe（ADT の定義と render の実装）
// パス: src/shared/infrastructure/html/Html.flix 相当
mod SharedOkHtml {
    pub enum Html {
        case Text(String)
        case RawUnsafe(String)
    }

    pub def render(html: Html): String = match html {
        case Html.Text(s)      => escapeText(s)
        case Html.RawUnsafe(s) => s
    }
}
