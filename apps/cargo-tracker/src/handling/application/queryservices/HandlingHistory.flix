///
/// 荷役履歴の照会（US15。IT10）。
///
/// **Read Model として扱う**（設計の `HandlingActivityHistory`）。
///
mod HandlingHistory {
    use HandlingModel.HandlingActivity
    use HandlingRepo.HandlingRepo

    /// 新しい順に一覧する（永続化側が並べる）
    pub def listAll(): List[HandlingActivity] \ HandlingRepo =
        HandlingRepo.findAll()
}
