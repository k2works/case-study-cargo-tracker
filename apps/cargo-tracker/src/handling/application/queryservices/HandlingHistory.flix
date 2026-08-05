///
/// 荷役履歴の照会（US15。IT10。IT11 で絞り込みと上限を入れた）。
///
/// **Read Model として扱う**（設計の `HandlingActivityHistory`）。
///
mod HandlingHistory {
    use HandlingModel.HandlingActivity
    use HandlingRepo.HandlingRepo

    ///
    /// 一度に出す件数の上限（IT11・H9）。
    ///
    /// 追跡管理者が 1 画面で目を通せる量にする。IT9 の経路候補（20 件）と
    /// 揃える必要は無い——荷役は 1 貨物あたりの回数が少なく、
    /// 絞り込んだ結果は通常 10 件に満たない。上限が効くのは**絞り込まないとき**であり、
    /// そこは「最近何が起きたか」を見る用途である。
    ///
    pub def displayLimit(): Int32 = 50

    ///
    /// 履歴を引く。**返すのは (表示分, 条件に合う総数) である**。
    ///
    /// 総数を一緒に返すのは、落とした件数を画面が出せるようにするためである。
    /// 表示分の長さだけでは「ちょうど上限だった」のか「切られた」のかが分からない。
    ///
    pub def search(trackingNumber: Option[String]):
            (List[HandlingActivity], Int32) \ HandlingRepo =
        let key = trackingNumber |> Option.flatMap(normalize);
        (HandlingRepo.findRecent(key, displayLimit()), HandlingRepo.countMatching(key))

    ///
    /// 空白だけの入力は「絞り込まない」と同じに扱う。
    ///
    /// 検索欄を空のまま送るのは日常的に起きる。空文字で条件を組むと
    /// 1 件も出ず、「記録が無い」と読まれる。
    ///
    def normalize(value: String): Option[String] =
        let trimmed = String.trim(value);
        if (trimmed == "") None else Some(trimmed)
}
