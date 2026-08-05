///
/// 荷役作業の永続化ポート。
///
mod HandlingRepo {
    use HandlingModel.HandlingActivity

    pub eff HandlingRepo {
        /// 荷役作業を保存する（記録は追記であり、更新しない）
        def save(activity: HandlingActivity): Unit

        ///
        /// 荷役履歴を新しい順に一覧する（IT11・H9 で絞り込みと上限を入れた）。
        ///
        /// **Read Model として扱う**（設計の `HandlingActivityHistory`）。
        /// 集約を再構築して返すが、書き込みには使わない——
        /// 荷役の記録は追記であり、過去の記録を書き換える操作が無い。
        ///
        /// `trackingNumber` が `Some` ならその貨物だけを返す。追跡管理者は
        /// 「この番号の貨物は今どこか」に答えるために開くのであり、
        /// **全貨物が時系列で混ざった一覧では業務が回らない**（IT10 レビュー H9）。
        ///
        /// `limit` は返す件数の上限である。**上限を超えた分は
        /// `countMatching` との差として画面に出す**——黙って切ると
        /// 「これで全部」と読まれる。
        ///
        def findRecent(trackingNumber: Option[String], limit: Int32):
                List[HandlingActivity]

        ///
        /// 条件に合う総件数。
        ///
        /// `findRecent` と**同じ条件**で数える。落とした件数を画面に出すために要る。
        ///
        def countMatching(trackingNumber: Option[String]): Int32
    }
}
