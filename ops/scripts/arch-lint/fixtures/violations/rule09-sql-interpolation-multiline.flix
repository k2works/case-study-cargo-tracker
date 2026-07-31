/// 負例: SQL の変数補間を複数行の連結に分けた形
/// 行単位の走査ではすり抜けるため、論理行に畳んでから照合する必要がある
mod BadMultilineSql {
    pub def find(n: String): String =
        "SELECT * FROM tracking_activity " +
        "WHERE tracking_number = '${n}'"
}
