// 適法: 状態を変えない GET フォーム（絞り込み）
// パス: src/shipper/interfaces/web/ShipperPages.flix 相当
//
// 規約 8 の目的は **CSRF トークンの付け忘れを防ぐ**ことである。
// CSRF は「他サイトから利用者の権限で状態を変えさせられる」攻撃であり、
// 状態を変えない GET フォームには当てはまらない。
//
// GET フォームに `Components.form` を使うと、意味のない `_csrf` 隠しフィールドが
// URL のクエリに載る。**要らない防御を足すと、要る防御との区別が付かなくなる**。
mod ShipperOkSearchForm {
    def searchBox(keyword: String): Html =
        element("form", attr("method", "get") :: attr("action", "/shippers") :: Nil,
            element("input", attr("type", "text") :: attr("name", "q") ::
                             attr("value", keyword) :: Nil, Nil) ::
            element("button", attr("type", "submit") :: Nil,
                text("絞り込む") :: Nil) :: Nil)
}
