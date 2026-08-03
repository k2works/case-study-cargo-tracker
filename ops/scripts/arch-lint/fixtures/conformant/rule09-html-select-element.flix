// 適法: HTML の `<select>` 要素を組み立てる（SQL ではない）
// パス: src/routing/interfaces/web/VoyagePages.flix 相当
//
// 規約 9 は「SQL に変数を文字列補間しない」ことを見る。
// `select` は HTML の要素名でもあり、**単語として一致するだけでは
// SQL と区別できない**。IT6 で実際に偽陽性が出た。
//
// SQL の骨格（`SELECT ... FROM` / `INSERT INTO` / `UPDATE ... SET` /
// `DELETE FROM`）を要求すれば、HTML の要素名とは区別できる。
mod RoutingOkVoyagePages {
    def locationSelect(name: String, selected: String,
                       locations: List[(String, String)]): Html =
        element("select", attr("name", name) :: class_("form-select") :: Nil,
            locations |> List.map(pair -> {
                let (code, locationName) = pair;
                element("option", attr("value", code) :: Nil,
                    text("${locationName} (${code})") :: Nil)
            }))
}
