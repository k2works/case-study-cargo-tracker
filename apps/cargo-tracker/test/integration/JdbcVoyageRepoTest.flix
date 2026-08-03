///
/// 航海リポジトリの JDBC 実装の統合テスト。
///
/// **2 テーブルにまたがる集約**を扱うため、単体テストでは見えない性質が多い。
/// 区間の順序・入れ替え時の残存・航海番号の一意制約・検索条件の組み合わせは、
/// インメモリのハンドラでは再現しない。
///
mod JdbcVoyageRepoTest {
    import java.lang.Exception

    use RoutingJdbcVoyageRepo.withJdbcVoyageRepo
    use RoutingModel.{Voyage, CarrierMovement, CargoCapability}
    use RoutingModel.{registerVoyage, movement}
    use SharedDbMigration.migrate
    use SharedDbPool.{create, h2Config, close}
    use SharedDbPool.DataSource
    use SharedDbTx.{transactional, readOnly}
    use VoyageRepo.VoyageRepo

    def setup(dbName: String): DataSource \ IO =
        let config = h2Config(dbName);
        discard migrate(config);
        let ds = create(config);
        // 区間は `location` への外部キーを持つ。マスタが要る
        SharedDbDevSeed.seed(ds);
        ds

    def voyageOf(number: String, movements: List[CarrierMovement],
                 capabilities: List[CargoCapability]): Voyage =
        match registerVoyage(number, "SS Sakura", "Sakura Line", movements, capabilities) {
            case Ok(v)  => v
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        }

    def direct(): List[CarrierMovement] =
        movement("JPOSA", "USLAX", "2026-11-01T10:00", "2026-11-15T08:00") :: Nil

    def viaTokyo(): List[CarrierMovement] =
        movement("JPOSA", "JPTYO", "2026-11-01T10:00", "2026-11-02T09:00") ::
        movement("JPTYO", "USLAX", "2026-11-03T10:00", "2026-11-15T08:00") :: Nil

    def general(): List[CargoCapability] = CargoCapability.General :: Nil

    // ---- 往復 ----

    ///
    /// 保存した航海を読み戻せること（往復）。
    ///
    /// **片道だけのテストは、書けているが読めていない状態を見逃す**。
    ///
    @Test
    def testSavesAndReadsBackVoyage(): Unit \ Assert + IO =
        let ds = setup("voyage_roundtrip");
        transactional(ds, () -> withJdbcVoyageRepo(() ->
            VoyageRepo.save(voyageOf("V-0100S", direct(), general()))));
        let found = readOnly(ds, () -> withJdbcVoyageRepo(() ->
            VoyageRepo.findByVoyageNumber("V-0100S")));
        close(ds);
        match found {
            case None    => Assert.fail("保存した航海を読み戻せなかった")
            case Some(v) =>
                Assert.assertEq(expected = "V-0100S", RoutingModel.voyageNumberOf(v));
                Assert.assertEq(expected = "SS Sakura", RoutingModel.vesselNameOf(v));
                Assert.assertEq(expected = "Sakura Line", RoutingModel.carrierNameOf(v));
                Assert.assertEq(expected = "JPOSA", RoutingModel.originOf(v));
                Assert.assertEq(expected = "USLAX", RoutingModel.destinationOf(v))
        }

    ///
    /// 時刻が桁落ちせずに往復すること。
    ///
    /// 内部表現は `YYYY-MM-DDTHH:MM`、DB は `TIMESTAMP`。
    /// **分まで保たれること**を確かめる（秒を補って保存し、戻すときに落とす）。
    ///
    @Test
    def testMovementTimesRoundTrip(): Unit \ Assert + IO =
        let ds = setup("voyage_times");
        transactional(ds, () -> withJdbcVoyageRepo(() ->
            VoyageRepo.save(voyageOf("V-0200S", direct(), general()))));
        let found = readOnly(ds, () -> withJdbcVoyageRepo(() ->
            VoyageRepo.findByVoyageNumber("V-0200S")));
        close(ds);
        match found {
            case None    => Assert.fail("保存した航海を読み戻せなかった")
            case Some(v) => match List.head(RoutingModel.movementsOf(v)) {
                case None    => Assert.fail("区間が読み戻せなかった")
                case Some(m) =>
                    Assert.assertEq(expected = "2026-11-01T10:00",
                                    RoutingModel.departureTimeOf(m));
                    Assert.assertEq(expected = "2026-11-15T08:00",
                                    RoutingModel.arrivalTimeOf(m))
            }
        }

    ///
    /// 区間が**順序どおり**に読み戻せること。
    ///
    /// 順序が崩れると、航程の端点（出発地・仕向地）が入れ替わる。
    ///
    @Test
    def testMovementsKeepOrder(): Unit \ Assert + IO =
        let ds = setup("voyage_order");
        transactional(ds, () -> withJdbcVoyageRepo(() ->
            VoyageRepo.save(voyageOf("V-0300S", viaTokyo(), general()))));
        let found = readOnly(ds, () -> withJdbcVoyageRepo(() ->
            VoyageRepo.findByVoyageNumber("V-0300S")));
        close(ds);
        match found {
            case None    => Assert.fail("保存した航海を読み戻せなかった")
            case Some(v) =>
                let seqs = RoutingModel.movementsOf(v) |> List.map(RoutingModel.seqNumberOf);
                Assert.assertEq(expected = 1 :: 2 :: Nil, seqs);
                Assert.assertEq(expected = "JPOSA", RoutingModel.originOf(v));
                Assert.assertEq(expected = "USLAX", RoutingModel.destinationOf(v));
                Assert.assertEq(expected = "JPTYO" :: Nil, RoutingModel.portsOfCallOf(v))
        }

    /// 対応貨物種別が往復すること
    @Test
    def testCapabilitiesRoundTrip(): Unit \ Assert + IO =
        let ds = setup("voyage_capabilities");
        transactional(ds, () -> withJdbcVoyageRepo(() ->
            VoyageRepo.save(voyageOf("V-0400S", direct(),
                CargoCapability.General :: CargoCapability.Refrigerated :: Nil))));
        let found = readOnly(ds, () -> withJdbcVoyageRepo(() ->
            VoyageRepo.findByVoyageNumber("V-0400S")));
        close(ds);
        match found {
            case None    => Assert.fail("保存した航海を読み戻せなかった")
            case Some(v) =>
                Assert.assertTrue(RoutingModel.supports(v, CargoCapability.General));
                Assert.assertTrue(RoutingModel.supports(v, CargoCapability.Refrigerated));
                Assert.assertTrue(not RoutingModel.supports(v, CargoCapability.Hazardous))
        }

    // ---- 一意制約 ----

    ///
    /// 同一航海番号を 2 度登録できないこと（RT-1）。
    ///
    /// **アプリの事前確認をすり抜けた同時登録**は DB の一意制約で止まる。
    /// ここで例外になることを確かめておかないと、
    /// 「事前確認があるから大丈夫」という前提が検証されないまま残る。
    ///
    @Test
    def testRejectsDuplicateVoyageNumber(): Unit \ Assert + IO =
        let ds = setup("voyage_duplicate");
        transactional(ds, () -> withJdbcVoyageRepo(() ->
            VoyageRepo.save(voyageOf("V-0500S", direct(), general()))));
        let rejected = try {
            transactional(ds, () -> withJdbcVoyageRepo(() ->
                VoyageRepo.save(voyageOf("V-0500S", direct(), general()))));
            false
        } catch {
            case _: Exception => true
        };
        close(ds);
        Assert.assertTrue(rejected)

    // ---- 更新（US25） ----

    ///
    /// 区間を減らす更新で**古い行が残らない**こと。
    ///
    /// 差分更新にすると、2 区間 → 1 区間の更新で 2 件目が残り、
    /// 航程が伸びたままになる。削除してから挿入する。
    ///
    @Test
    def testReplaceRemovesOldMovements(): Unit \ Assert + IO =
        let ds = setup("voyage_replace");
        transactional(ds, () -> withJdbcVoyageRepo(() ->
            VoyageRepo.save(voyageOf("V-0600S", viaTokyo(), general()))));
        transactional(ds, () -> withJdbcVoyageRepo(() ->
            VoyageRepo.replace(voyageOf("V-0600S", direct(), general()))));
        let found = readOnly(ds, () -> withJdbcVoyageRepo(() ->
            VoyageRepo.findByVoyageNumber("V-0600S")));
        close(ds);
        match found {
            case None    => Assert.fail("更新した航海を読み戻せなかった")
            case Some(v) =>
                Assert.assertEq(expected = 1, List.length(RoutingModel.movementsOf(v)));
                Assert.assertEq(expected = "USLAX", RoutingModel.destinationOf(v));
                Assert.assertTrue(List.isEmpty(RoutingModel.portsOfCallOf(v)))
        }

    /// 航海の属性（船名・運送会社・対応種別）も更新されること
    @Test
    def testReplaceUpdatesAttributes(): Unit \ Assert + IO =
        let ds = setup("voyage_replace_attrs");
        transactional(ds, () -> withJdbcVoyageRepo(() ->
            VoyageRepo.save(voyageOf("V-0700S", direct(), general()))));
        let updated = match registerVoyage("V-0700S", "SS Kaede", "Kaede Line",
                                           direct(), CargoCapability.Hazardous :: Nil) {
            case Ok(v)  => v
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        };
        transactional(ds, () -> withJdbcVoyageRepo(() -> VoyageRepo.replace(updated)));
        let found = readOnly(ds, () -> withJdbcVoyageRepo(() ->
            VoyageRepo.findByVoyageNumber("V-0700S")));
        close(ds);
        match found {
            case None    => Assert.fail("更新した航海を読み戻せなかった")
            case Some(v) =>
                Assert.assertEq(expected = "SS Kaede", RoutingModel.vesselNameOf(v));
                Assert.assertEq(expected = "Kaede Line", RoutingModel.carrierNameOf(v));
                Assert.assertTrue(RoutingModel.supports(v, CargoCapability.Hazardous));
                Assert.assertTrue(not RoutingModel.supports(v, CargoCapability.General))
        }

    // ---- 検索（US07） ----

    /// 複数の航海を登録する
    def seedVoyages(ds: DataSource): Unit \ IO =
        transactional(ds, () -> withJdbcVoyageRepo(() -> {
            // 大阪 → LA（一般貨物）
            VoyageRepo.save(voyageOf("V-1000S", direct(), general()));
            // 大阪 → 東京 → LA（冷凍対応）
            VoyageRepo.save(voyageOf("V-2000S", viaTokyo(),
                CargoCapability.Refrigerated :: Nil));
            // 東京 → LA（一般貨物・出発が遅い）
            VoyageRepo.save(voyageOf("V-3000S",
                movement("JPTYO", "USLAX", "2026-12-01T10:00", "2026-12-15T08:00") :: Nil,
                general()))
        }))

    def search(ds: DataSource, origin: String, destination: String,
               departureFrom: String, departureTo: String,
               capability: Option[CargoCapability]): List[String] \ IO =
        readOnly(ds, () -> withJdbcVoyageRepo(() ->
            VoyageRepo.search(origin, destination, departureFrom, departureTo, capability)))
            |> List.map(RoutingModel.voyageNumberOf)

    /// 条件を指定しなければ全件が出ること
    @Test
    def testSearchWithoutCriteriaReturnsAll(): Unit \ Assert + IO =
        let ds = setup("voyage_search_all");
        seedVoyages(ds);
        let found = search(ds, "", "", "", "", None);
        close(ds);
        Assert.assertEq(expected = 3, List.length(found))

    ///
    /// 出発地は**航程全体の端点**で絞ること。
    ///
    /// 区間単位で一致させると、大阪 → 東京 → LA の航海が
    /// 「東京発」としても出てしまう。
    ///
    @Test
    def testSearchByOriginUsesVoyageEndpoint(): Unit \ Assert + IO =
        let ds = setup("voyage_search_origin");
        seedVoyages(ds);
        let found = search(ds, "JPTYO", "", "", "", None);
        close(ds);
        // 東京発は V-3000S だけ（V-2000S は東京を経由するが大阪発）
        Assert.assertEq(expected = "V-3000S" :: Nil, found)

    /// 仕向地で絞れること
    @Test
    def testSearchByDestination(): Unit \ Assert + IO =
        let ds = setup("voyage_search_destination");
        seedVoyages(ds);
        let found = search(ds, "", "USLAX", "", "", None);
        close(ds);
        Assert.assertEq(expected = 3, List.length(found))

    ///
    /// 出発期間で絞れること。**指定日当日を含む**こと。
    ///
    /// 日付だけで比較すると、指定日当日の便が丸ごと落ちる
    /// （IT5 の到着期限と同じ形の境界）。
    ///
    @Test
    def testSearchByDepartureRangeIncludesBoundaryDays(): Unit \ Assert + IO =
        let ds = setup("voyage_search_range");
        seedVoyages(ds);
        // 11/01 出発の 2 件だけが範囲に入る
        let inRange = search(ds, "", "", "2026-11-01", "2026-11-01", None);
        // 12/01 出発の 1 件
        let later = search(ds, "", "", "2026-12-01", "2026-12-31", None);
        close(ds);
        Assert.assertEq(expected = 2, List.length(inRange));
        Assert.assertEq(expected = "V-3000S" :: Nil, later)

    ///
    /// 貨物種別で絞れること（US07-6）。
    ///
    /// 冷凍貨物を運べる航海だけが残る。
    ///
    @Test
    def testSearchByCapability(): Unit \ Assert + IO =
        let ds = setup("voyage_search_capability");
        seedVoyages(ds);
        let refrigerated = search(ds, "", "", "", "", Some(CargoCapability.Refrigerated));
        let hazardous = search(ds, "", "", "", "", Some(CargoCapability.Hazardous));
        close(ds);
        Assert.assertEq(expected = "V-2000S" :: Nil, refrigerated);
        // 危険物対応の航海は無い
        Assert.assertTrue(List.isEmpty(hazardous))

    ///
    /// 条件を**重ねて**絞れること。
    ///
    /// 片方だけが効いていると、利用者は「絞ったのに関係ない便が出る」ことになる。
    ///
    @Test
    def testSearchCombinesCriteria(): Unit \ Assert + IO =
        let ds = setup("voyage_search_combined");
        seedVoyages(ds);
        let found = search(ds, "JPOSA", "USLAX", "2026-11-01", "2026-11-30",
                           Some(CargoCapability.General));
        close(ds);
        Assert.assertEq(expected = "V-1000S" :: Nil, found)
}
