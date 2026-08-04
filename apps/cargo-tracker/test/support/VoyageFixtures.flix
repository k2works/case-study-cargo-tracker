///
/// 航海スケジュールのテストフィクスチャ（IT8・US01）。
///
/// # なぜ HTTP 経由で作らないか
///
/// `TestApp` の方針は「**利用者と同じ経路で状態を作れるならそうする**」である。
/// 航海の登録画面は存在する（US24）ので、原則からは HTTP で作るべきである。
///
/// しかし航海登録フォームは**区間ごとに 4 つの入力欄**を持ち、
/// 1 便を作るだけで CSRF の取得と 10 個以上のパラメータが要る。
/// 見積のテストが確かめたいのは「候補が出ること」であり、
/// **航海登録フォームの入力形式ではない**。フォームが変わるたびに
/// 見積のテストが落ちる状態は、テストが何を守っているのかを曖昧にする。
///
/// 航海登録の経路そのものは `VoyageHttpTest` が HTTP で固定している。
/// **同じことを 2 度確かめない**。
///
mod VoyageFixtures {
    use RoutingJdbcVoyageRepo.withJdbcVoyageRepo
    use RoutingModel.{Voyage, CargoCapability, movement, registerVoyage}
    use SharedDbPool.DataSource
    use SharedDbTx.transactional
    use VoyageRepo.VoyageRepo

    def voyageOf(number: String, movements: List[RoutingModel.CarrierMovement],
                 capabilities: List[CargoCapability]): Voyage =
        match registerVoyage(number, "SS Sakura", "Sakura Line", movements, capabilities) {
            case Ok(v)  => v
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        }

    ///
    /// 大阪 → ロサンゼルスの便を 2 本入れる（直行と東京経由）。
    ///
    /// **出発を 2099 年にする**。`RouteFinder` は今日より前に出発する便を
    /// 候補にしないため、実日付に近い値だと**その日が過ぎた瞬間に
    /// 候補が 0 件になり、テストが落ちる**（IT7 の `RouteAssignHttpTest` で
    /// 実際に起きた形）。
    ///
    /// 直行と経由の 2 本を入れるのは、**候補が 1 本だけだと
    /// 「絞り込めた」と「たまたま 1 本だった」が区別できない**ためである。
    /// 積替のある候補のほうが高くなることも、これで確かめられる。
    ///
    pub def seedOsakaToLosAngeles(ds: DataSource): Unit \ IO =
        transactional(ds, () -> withJdbcVoyageRepo(() -> {
            // 直行（1 区間）
            VoyageRepo.save(voyageOf("V-9001S",
                movement("JPOSA", "USLAX", "2099-01-05T10:00", "2099-01-20T08:00") :: Nil,
                CargoCapability.General :: CargoCapability.Refrigerated :: Nil));
            // 東京での積替（**別々の航海 2 本**）。
            //
            // 1 本の航海の中に東京を挟むだけでは積替にならない——
            // `RouteFinder` は 1 航海内の寄港地を区間として展開するため、
            // 大阪発ロサンゼルス行きの**直行の別解**として扱う（経由港は出ない）。
            // これは実務どおりである。同じ船に乗ったままなら積み替えていない。
            VoyageRepo.save(voyageOf("V-9002S",
                movement("JPOSA", "JPTYO", "2099-01-03T10:00", "2099-01-04T09:00") :: Nil,
                CargoCapability.General :: Nil));
            VoyageRepo.save(voyageOf("V-9003S",
                movement("JPTYO", "USLAX", "2099-01-06T10:00", "2099-01-22T08:00") :: Nil,
                CargoCapability.General :: Nil))
        }))
}
