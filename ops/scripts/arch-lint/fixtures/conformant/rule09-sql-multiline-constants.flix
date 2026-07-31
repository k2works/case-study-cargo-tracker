/// 正例: 定数同士の複数行連結は可読性のために許容する（変数の埋め込みがない）
mod OkMultilineSql {
    pub def selectEventsSql(): String =
        "SELECT e.event_time, e.event_type " +
        "FROM tracking_handling_event e " +
        "WHERE e.tracking_id = ?"
}
