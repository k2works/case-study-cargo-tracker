// 違反: SQL の骨格を別の関数へ切り出し、条件だけを補間で連結する
// パス: src/routing/infrastructure/repositories/JdbcVoyageRepo.flix 相当
//
// 骨格（`SELECT ... FROM`）が同じ論理行に現れないため、骨格だけを要求する
// 照合では素通しになる。**規約が守らせたい書き方をしているファイルほど
// 検査から外れる**という逆転が起きていた（IT6 レビュー）。
//
// 断片を返す関数（`selectColumns()`）と SQL 句（`WHERE`）の組み合わせで拾う。
mod RoutingNgFragmentSql {
    def searchSql(voyageNumber: String): String =
        selectColumns() + "WHERE v.voyage_number = '${voyageNumber}'"
}
