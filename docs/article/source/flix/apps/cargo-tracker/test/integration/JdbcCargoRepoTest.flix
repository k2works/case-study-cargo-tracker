///
/// 貨物リポジトリの JDBC 実装の統合テスト。
///
/// IT4 のレビュー M10 で「`NUMERIC` の往復と部分 NULL のテストがない」と
/// 指摘された分を、US05 の特別要件とあわせて固定する。
///
/// **単体テストでは原理的に見つからない**種類を扱う。桁落ち・NULL の扱い・
/// 未知の永続化値は、インメモリのハンドラでは再現しない
/// （IT4 では `TxMode.ReadOnly` が何も守っていないことを統合テストが捕まえた）。
///
mod JdbcCargoRepoTest {
    use BookingJdbcCargoRepo.withJdbcCargoRepo
    use BookingModel.{Cargo, CargoType, BookingStatus, TemperatureUnit}
    use BookingModel.{bookCargo, requireHazardousDeclaration, requireTemperatureRequirement}
    use CargoRepo.CargoRepo
    use SharedDbMigration.migrate
    use SharedDbPool.{create, h2Config, close}
    use SharedDbPool.DataSource
    use SharedDbTx.{transactional, readOnly}

    def setup(dbName: String): DataSource \ IO =
        let config = h2Config(dbName);
        discard migrate(config);
        create(config)

    def aBookingId(): String = "BK-3F2504E0"
    def aShipperId(): String = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"

    def cargoOf(cargoType: CargoType,
                dimensions: Option[(Int32, Int32, Int32)],
                quantity: Option[Int32],
                description: Option[String],
                hazardous: Option[BookingModel.HazardousDeclaration],
                temperature: Option[BookingModel.TemperatureRequirement]): Cargo =
        match bookCargo(aBookingId(), aShipperId(), "JPOSA", "USLAX", "2026-12-31", "2026-01-01",
                        cargoType, 1200500i64, dimensions, quantity, description,
                        hazardous, temperature) {
            case Ok(c)  => c
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        }

    def saveAndRead(dbName: String, cargo: Cargo): Option[Cargo] \ IO =
        let ds = setup(dbName);
        transactional(ds, () -> withJdbcCargoRepo(() -> CargoRepo.save(cargo)));
        let found = readOnly(ds, () -> withJdbcCargoRepo(() ->
            CargoRepo.findByBookingId(aBookingId())));
        close(ds);
        found

    def aDeclaration(): BookingModel.HazardousDeclaration =
        match requireHazardousDeclaration("3", "UN1203", "ガソリン") {
            case Ok(d)  => d
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        }

    def aTemperature(): BookingModel.TemperatureRequirement =
        match requireTemperatureRequirement(-25500i64, -18000i64, "CELSIUS") {
            case Ok(t)  => t
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        }

    // ---- 数値の往復（レビュー M10） ----

    ///
    /// 重量が桁落ちせずに往復すること。
    ///
    /// 内部はグラムの整数、DB は `NUMERIC(10,3)`（キログラム）。
    /// `Float64` を挟むと 1200.500 が 1200.4999... になり、
    /// **復元時に 1200499 グラムへ落ちる**。1 グラムの差でも、
    /// 「保存した値と読み出した値が違う」ことに変わりはない。
    ///
    @Test
    def testWeightRoundTripsWithoutPrecisionLoss(): Unit \ Assert + IO =
        match saveAndRead("cargo_weight_roundtrip",
                          cargoOf(CargoType.General, None, None, None, None, None)) {
            case None    => Assert.fail("保存した貨物を読み戻せなかった")
            case Some(c) => Assert.assertEq(expected = 1200500i64,
                                            BookingModel.weightGramsOf(c))
        }

    ///
    /// 温度が桁落ちせずに往復すること。
    ///
    /// 内部は千分の一度の整数、DB は `NUMERIC(10,3)`（度）。
    /// **負の値**であることが重要で、符号の扱いを誤ると保冷条件が正反対になる。
    ///
    @Test
    def testTemperatureRoundTripsWithSign(): Unit \ Assert + IO =
        match saveAndRead("cargo_temperature_roundtrip",
                          cargoOf(CargoType.Refrigerated, None, None, None,
                                  None, Some(aTemperature()))) {
            case None    => Assert.fail("保存した貨物を読み戻せなかった")
            case Some(c) => match BookingModel.temperatureRequirementOf(c) {
                case None    => Assert.fail("温度条件が読み戻せなかった")
                case Some(t) =>
                    Assert.assertEq(expected = -25500i64,
                                    BookingModel.minTemperatureMilliOf(t));
                    Assert.assertEq(expected = -18000i64,
                                    BookingModel.maxTemperatureMilliOf(t));
                    Assert.assertTrue(BookingModel.temperatureUnitOf(t) == TemperatureUnit.Celsius)
            }
        }

    /// 危険物申告が往復すること
    @Test
    def testHazardousDeclarationRoundTrips(): Unit \ Assert + IO =
        match saveAndRead("cargo_hazardous_roundtrip",
                          cargoOf(CargoType.Hazardous, None, None, None,
                                  Some(aDeclaration()), None)) {
            case None    => Assert.fail("保存した貨物を読み戻せなかった")
            case Some(c) => match BookingModel.hazardousDeclarationOf(c) {
                case None    => Assert.fail("危険物申告が読み戻せなかった")
                case Some(d) =>
                    Assert.assertEq(expected = "3", BookingModel.hazardousClassOf(d));
                    Assert.assertEq(expected = "UN1203", BookingModel.unNumberOf(d));
                    Assert.assertEq(expected = "ガソリン", BookingModel.properShippingNameOf(d))
            }
        }

    /// 寸法が往復すること
    @Test
    def testDimensionsRoundTrip(): Unit \ Assert + IO =
        match saveAndRead("cargo_dimensions_roundtrip",
                          cargoOf(CargoType.General, Some((1200, 800, 1000)), Some(5),
                                  Some("機械部品"), None, None)) {
            case None    => Assert.fail("保存した貨物を読み戻せなかった")
            case Some(c) =>
                Assert.assertEq(expected = Some((1200, 800, 1000)),
                                BookingModel.dimensionsOf(c));
                Assert.assertEq(expected = Some(5), BookingModel.quantityOf(c));
                Assert.assertEq(expected = Some("機械部品"), BookingModel.descriptionOf(c))
        }

    ///
    /// 寸法が **DB 上でセンチメートル**として保存されること。
    ///
    /// ドメインはミリメートルの整数、DB 列は `NUMERIC(10,3)`（cm）である
    /// （`data-model.md`）。読み書きの両端が同じ単位である限り往復テストは
    /// 緑になるため、**アプリの外から読む人だけが 10 倍の値を見る**という
    /// 壊れ方をする。帳票・BI・他システムはこの列を cm として読む。
    ///
    /// 重量（g→kg）・温度（milli→度）はスケールしているのに、
    /// 寸法だけしていなかった（IT5 レビューで発見）。
    ///
    @Test
    def testDimensionsAreStoredInCentimeters(): Unit \ Assert + IO =
        let ds = setup("cargo_dimension_unit");
        transactional(ds, () -> withJdbcCargoRepo(() ->
            CargoRepo.save(cargoOf(CargoType.General, Some((1200, 800, 1005)),
                                   None, None, None, None))));
        let stored = readDimensionLength(ds);
        close(ds);
        // 1200 mm = 120.0 cm
        Assert.assertEq(expected = "120.000", stored)

    /// `dimension_length` を DB から文字列として読む（アプリの変換を通さない）
    def readDimensionLength(ds: DataSource): String \ IO =
        region rc {
            let result = Ref.fresh(rc, "");
            SharedDbPool.withConnection(ds, conn ->
                SharedDbPool.withStatement(conn, stmt -> {
                    let rs = stmt.executeQuery(
                        "SELECT dimension_length FROM cargo WHERE booking_id = '${aBookingId()}'");
                    discard rs.next();
                    Ref.put(rs.getBigDecimal(1).toPlainString(), result);
                    rs.close()
                }));
            Ref.get(result)
        }

    ///
    /// センチメートル未満の桁が保たれること。
    ///
    /// `NUMERIC(10,3)` は小数第 3 位まで持てる。1005 mm = 100.5 cm を
    /// 整数へ丸めると 5 mm が消える。
    ///
    @Test
    def testDimensionKeepsSubCentimeterPrecision(): Unit \ Assert + IO =
        match saveAndRead("cargo_dimension_precision",
                          cargoOf(CargoType.General, Some((1200, 800, 1005)),
                                  None, None, None, None)) {
            case None    => Assert.fail("保存した貨物を読み戻せなかった")
            case Some(c) => Assert.assertEq(expected = Some((1200, 800, 1005)),
                                            BookingModel.dimensionsOf(c))
        }

    // ---- 部分 NULL（レビュー M10） ----

    ///
    /// 一般貨物では特別要件の列がすべて NULL で往復すること。
    ///
    /// **空文字で埋めない**。空文字の危険物クラスを持つ行は、
    /// 「申告があるが中身がない」という書類として成立しない状態になる。
    ///
    @Test
    def testGeneralCargoHasNoSpecialRequirements(): Unit \ Assert + IO =
        match saveAndRead("cargo_general_nulls",
                          cargoOf(CargoType.General, None, None, None, None, None)) {
            case None    => Assert.fail("保存した貨物を読み戻せなかった")
            case Some(c) =>
                Assert.assertTrue(Option.isEmpty(BookingModel.hazardousDeclarationOf(c)));
                Assert.assertTrue(Option.isEmpty(BookingModel.temperatureRequirementOf(c)));
                Assert.assertTrue(Option.isEmpty(BookingModel.dimensionsOf(c)));
                Assert.assertTrue(Option.isEmpty(BookingModel.quantityOf(c)));
                Assert.assertTrue(Option.isEmpty(BookingModel.descriptionOf(c)))
        }

    ///
    /// 危険物では温度の列が NULL であること（その逆も）。
    ///
    /// 種別を取り違えた行が作られていないことを、**永続化された形**で確かめる。
    ///
    @Test
    def testHazardousCargoHasNoTemperature(): Unit \ Assert + IO =
        match saveAndRead("cargo_hazardous_no_temp",
                          cargoOf(CargoType.Hazardous, None, None, None,
                                  Some(aDeclaration()), None)) {
            case None    => Assert.fail("保存した貨物を読み戻せなかった")
            case Some(c) => Assert.assertTrue(
                Option.isEmpty(BookingModel.temperatureRequirementOf(c)))
        }

    @Test
    def testRefrigeratedCargoHasNoHazardousDeclaration(): Unit \ Assert + IO =
        match saveAndRead("cargo_refrigerated_no_hazard",
                          cargoOf(CargoType.Refrigerated, None, None, None,
                                  None, Some(aTemperature()))) {
            case None    => Assert.fail("保存した貨物を読み戻せなかった")
            case Some(c) => Assert.assertTrue(
                Option.isEmpty(BookingModel.hazardousDeclarationOf(c)))
        }

    ///
    /// 寸法が部分的に NULL の行は「寸法なし」として読むこと。
    ///
    /// 3 つ揃って初めて寸法になる。欠けた値を 0 で埋めると、
    /// **体積 0 の貨物**が積載計算に載る。
    ///
    /// 部分 NULL の行はアプリからは作れないため、SQL で直接作って読ませる。
    /// **アプリが作れない行が DB に存在しうる**（手作業の修正・旧バージョン・
    /// 他システムの書き込み）以上、読み手はそれに耐える必要がある。
    ///
    @Test
    def testPartiallyNullDimensionsReadAsAbsent(): Unit \ Assert + IO =
        let ds = setup("cargo_partial_dimensions");
        transactional(ds, () -> withJdbcCargoRepo(() ->
            CargoRepo.save(cargoOf(CargoType.General, Some((1200, 800, 1000)),
                                   None, None, None, None))));
        // 幅だけを NULL にする
        SharedDbPool.withConnection(ds, conn ->
            SharedDbPool.withStatement(conn, stmt ->
                discard stmt.executeUpdate(
                    "UPDATE cargo SET dimension_width = NULL WHERE booking_id = '${aBookingId()}'")));
        let found = readOnly(ds, () -> withJdbcCargoRepo(() ->
            CargoRepo.findByBookingId(aBookingId())));
        close(ds);
        match found {
            case None    => Assert.fail("貨物を読み戻せなかった")
            case Some(c) => Assert.assertTrue(Option.isEmpty(BookingModel.dimensionsOf(c)))
        }

    ///
    /// 温度の単位が未知の値だった行は「温度条件なし」として読むこと。
    ///
    /// **摂氏へ倒さない**。華氏 -13°F を -13°C として読むと 12 度ずれ、
    /// 冷凍と冷蔵が入れ替わって貨物を傷める。
    ///
    @Test
    def testUnknownTemperatureUnitReadsAsAbsent(): Unit \ Assert + IO =
        let ds = setup("cargo_unknown_unit");
        transactional(ds, () -> withJdbcCargoRepo(() ->
            CargoRepo.save(cargoOf(CargoType.Refrigerated, None, None, None,
                                   None, Some(aTemperature())))));
        SharedDbPool.withConnection(ds, conn ->
            SharedDbPool.withStatement(conn, stmt ->
                discard stmt.executeUpdate(
                    "UPDATE cargo SET temperature_unit = 'KELVIN' WHERE booking_id = '${aBookingId()}'")));
        let found = readOnly(ds, () -> withJdbcCargoRepo(() ->
            CargoRepo.findByBookingId(aBookingId())));
        close(ds);
        match found {
            case None    => Assert.fail("貨物を読み戻せなかった")
            case Some(c) => Assert.assertTrue(
                Option.isEmpty(BookingModel.temperatureRequirementOf(c)))
        }
}
