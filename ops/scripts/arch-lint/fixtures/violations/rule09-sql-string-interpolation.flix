// 規約 9 違反: SQL に変数を文字列補間している
// パス: src/tracking/infrastructure/repositories/Bad.flix 相当
mod TrackingBadSql {
    pub def bad(trackingNumber: String): String =
        "SELECT * FROM tracking_activity WHERE tracking_number = '${trackingNumber}'"
}
