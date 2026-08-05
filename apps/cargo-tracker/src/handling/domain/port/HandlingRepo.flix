///
/// 荷役作業の永続化ポート。
///
mod HandlingRepo {
    use HandlingModel.HandlingActivity

    pub eff HandlingRepo {
        /// 荷役作業を保存する（記録は追記であり、更新しない）
        def save(activity: HandlingActivity): Unit

        ///
        /// 荷役履歴を新しい順に一覧する。
        ///
        /// **Read Model として扱う**（設計の `HandlingActivityHistory`）。
        /// 集約を再構築して返すが、書き込みには使わない——
        /// 荷役の記録は追記であり、過去の記録を書き換える操作が無い。
        ///
        def findAll(): List[HandlingActivity]
    }
}
