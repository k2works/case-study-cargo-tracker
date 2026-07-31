// 適法: Components モジュール内での form 構築
// パス: src/shared/infrastructure/html/Components.flix 相当
mod SharedOkComponents {
    pub def form(action: String, csrf: String, children: List[Html]): Html =
        element("form", attr("action", action) :: Nil,
            hiddenField("_csrf", csrf) :: children)
}
