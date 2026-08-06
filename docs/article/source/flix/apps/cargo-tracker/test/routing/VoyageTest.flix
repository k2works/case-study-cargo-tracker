///
/// `Voyage` 集約と値オブジェクトの単体テスト（US24・US25）。
///
/// 対応するビジネスルールは [ドメインモデル設計](../../../../docs/design/domain-model.md)
/// の Routing Context（`business_rule_traceability.md` の RT-1・RT-2・RT-3・RT-5・RT-6・RT-7）。
///
/// 効果を一切要求しない。DB もサーバも起動せず、ビジネスルールだけを固定する。
///
mod VoyageTest {
    use RoutingModel.{Voyage, CarrierMovement, CargoCapability, VoyageError}
    use RoutingModel.{registerVoyage, voyageNumberOf, vesselNameOf, carrierNameOf,
                      capabilitiesOf, movementsOf, movement,
                      departureLocationOf, arrivalLocationOf, seqNumberOf,
                      originOf, destinationOf, supports}

    def aVoyageNumber(): String = "V-0100S"

    /// 大阪 → ロサンゼルス（直行）
    def directLeg(): CarrierMovement =
        movement("JPOSA", "USLAX", "2026-11-01T10:00", "2026-11-15T08:00")

    /// 大阪 → 東京 → ロサンゼルス（寄港地 1 つ）
    def viaTokyo(): List[CarrierMovement] =
        movement("JPOSA", "JPTYO", "2026-11-01T10:00", "2026-11-02T09:00") ::
        movement("JPTYO", "USLAX", "2026-11-03T10:00", "2026-11-15T08:00") :: Nil

    def register(number: String, movements: List[CarrierMovement],
                 capabilities: List[CargoCapability]): Result[VoyageError, Voyage] =
        registerVoyage(number, "SS Sakura", "Sakura Line", movements, capabilities)

    def general(): List[CargoCapability] = CargoCapability.General :: Nil

    def errOf(r: Result[VoyageError, t]): Option[VoyageError] = match r {
        case Err(e) => Some(e)
        case Ok(_)  => None
    }

    // ---- RT-1: 航海番号 ----

    @Test
    def testRegistersVoyage(): Unit \ Assert =
        match register(aVoyageNumber(), directLeg() :: Nil, general()) {
            case Err(e) => Assert.fail("登録に失敗した: ${e}")
            case Ok(v)  =>
                Assert.assertEq(expected = aVoyageNumber(), voyageNumberOf(v));
                Assert.assertEq(expected = "SS Sakura", vesselNameOf(v));
                Assert.assertEq(expected = "Sakura Line", carrierNameOf(v))
        }

    @Test
    def testRequiresVoyageNumber(): Unit \ Assert =
        Assert.assertEq(expected = Some(VoyageError.VoyageNumberIsRequired),
                        errOf(register("", directLeg() :: Nil, general())));
        Assert.assertEq(expected = Some(VoyageError.VoyageNumberIsRequired),
                        errOf(register("   ", directLeg() :: Nil, general())))

    ///
    /// 航海番号の長さ上限は DB 列（`VARCHAR(20)`）に合わせる。
    ///
    /// **アプリが受け入れた値を DB が拒否する**状態は、利用者から見て
    /// 「登録したのに保存されていない」という最も分かりにくい壊れ方になる。
    ///
    @Test
    def testRejectsTooLongVoyageNumber(): Unit \ Assert =
        Assert.assertEq(expected = Some(VoyageError.VoyageNumberIsTooLong),
                        errOf(register("V".repeat(21), directLeg() :: Nil, general())))

    @Test
    def testAcceptsVoyageNumberAtTheLimit(): Unit \ Assert =
        Assert.assertEq(expected = None,
                        errOf(register("V".repeat(20), directLeg() :: Nil, general())))

    // ---- 船名・運送会社 ----

    @Test
    def testRequiresVesselName(): Unit \ Assert =
        Assert.assertEq(expected = Some(VoyageError.VesselNameIsRequired),
                        errOf(registerVoyage(aVoyageNumber(), "", "Sakura Line",
                                             directLeg() :: Nil, general())))

    @Test
    def testRequiresCarrierName(): Unit \ Assert =
        Assert.assertEq(expected = Some(VoyageError.CarrierNameIsRequired),
                        errOf(registerVoyage(aVoyageNumber(), "SS Sakura", "",
                                             directLeg() :: Nil, general())))

    // ---- RT-5: 区間が 1 つ以上 ----

    ///
    /// 区間の無い航海は成立しないこと。
    ///
    /// 通すと**出発地も到着地も無い航海**が検索結果に出る。
    /// 経路候補算出（IT7）の入力としても使えない。
    ///
    @Test
    def testRejectsEmptySchedule(): Unit \ Assert =
        Assert.assertEq(expected = Some(VoyageError.ScheduleIsEmpty),
                        errOf(register(aVoyageNumber(), Nil, general())))

    // ---- RT-3: 区間の端点 ----

    @Test
    def testRejectsSameDepartureAndArrival(): Unit \ Assert =
        let leg = movement("JPOSA", "JPOSA", "2026-11-01T10:00", "2026-11-02T08:00");
        Assert.assertEq(expected = Some(VoyageError.MovementEndpointsAreSame),
                        errOf(register(aVoyageNumber(), leg :: Nil, general())))

    ///
    /// UN/LOCODE の形式を検証すること。
    ///
    /// **IT4 で作った `isUnLocode` と同じ規則**を使う（`Booking` の実装を
    /// 参照せず、共有の判定を使う。BC 独立性のため）。
    ///
    @Test
    def testRejectsMalformedLocationCode(): Unit \ Assert =
        let cases = "JPOS" :: "JPOSAX" :: "日本大阪港" :: "JPOS!" :: "" :: Nil;
        Assert.assertTrue(cases |> List.forAll(code -> {
            let leg = movement(code, "USLAX", "2026-11-01T10:00", "2026-11-02T08:00");
            errOf(register(aVoyageNumber(), leg :: Nil, general())) ==
                Some(VoyageError.LocationCodeIsMalformed)
        }))

    ///
    /// 小文字で入力された港コードを受け付けること。
    ///
    /// 利用者は小文字で打つ。**大文字化してから判定する**のは
    /// `Booking` と同じ規則である（共有カーネルの `isUnLocode` は
    /// 大文字を前提とし、正規化は呼び出し側が行う）。
    ///
    @Test
    def testAcceptsLowercaseLocationCode(): Unit \ Assert =
        let leg = movement("jposa", "uslax", "2026-11-01T10:00", "2026-11-02T08:00");
        match register(aVoyageNumber(), leg :: Nil, general()) {
            case Err(e) => Assert.fail("小文字の港コードが拒否された: ${e}")
            case Ok(v)  =>
                // 保持される値は**大文字へ正規化されている**こと
                Assert.assertEq(expected = "JPOSA", originOf(v));
                Assert.assertEq(expected = "USLAX", destinationOf(v))
        }

    // ---- RT-6: 時刻の順序 ----

    @Test
    def testRejectsInvertedTimes(): Unit \ Assert =
        let leg = movement("JPOSA", "USLAX", "2026-11-15T08:00", "2026-11-01T10:00");
        Assert.assertEq(expected = Some(VoyageError.MovementTimesAreInverted),
                        errOf(register(aVoyageNumber(), leg :: Nil, general())))

    ///
    /// 出発時刻と到着時刻が同一でも許すこと。
    ///
    /// **境界を閉じると当日便が登録できない**。IT5 の到着期限（当日は許す）と
    /// 同じ判断である。
    ///
    @Test
    def testAcceptsSameDepartureAndArrivalTime(): Unit \ Assert =
        let leg = movement("JPOSA", "USLAX", "2026-11-01T10:00", "2026-11-01T10:00");
        Assert.assertEq(expected = None, errOf(register(aVoyageNumber(), leg :: Nil, general())))

    @Test
    def testRejectsMalformedTime(): Unit \ Assert =
        let cases = "2026-11-01" :: "2026/11/01T10:00" :: "2026-13-01T10:00" ::
                    "2026-11-31T10:00" :: "2026-11-01T25:00" :: "" :: Nil;
        Assert.assertTrue(cases |> List.forAll(t -> {
            let leg = movement("JPOSA", "USLAX", t, "2026-12-01T10:00");
            errOf(register(aVoyageNumber(), leg :: Nil, general())) ==
                Some(VoyageError.MovementTimeIsMalformed)
        }))

    // ---- RT-2: 区間の連結と順序 ----

    ///
    /// 区間が連結していること（前の到着地 = 次の出発地）。
    ///
    /// 連結していない区間の並びは**同じ航海の航程ではない**。
    /// 通すと経路候補算出が成立しない経路を返す。
    ///
    @Test
    def testRejectsDisconnectedMovements(): Unit \ Assert =
        let legs =
            movement("JPOSA", "JPTYO", "2026-11-01T10:00", "2026-11-02T09:00") ::
            movement("CNSHA", "USLAX", "2026-11-03T10:00", "2026-11-15T08:00") :: Nil;
        Assert.assertEq(expected = Some(VoyageError.MovementsAreDisconnected),
                        errOf(register(aVoyageNumber(), legs, general())))

    ///
    /// 区間が時系列順であること（前の到着時刻 <= 次の出発時刻）。
    ///
    /// 逆転していると、到着前に次の区間が出発することになる。
    ///
    @Test
    def testRejectsOutOfOrderMovements(): Unit \ Assert =
        let legs =
            movement("JPOSA", "JPTYO", "2026-11-05T10:00", "2026-11-06T09:00") ::
            movement("JPTYO", "USLAX", "2026-11-01T10:00", "2026-11-15T08:00") :: Nil;
        Assert.assertEq(expected = Some(VoyageError.MovementsAreOutOfOrder),
                        errOf(register(aVoyageNumber(), legs, general())))

    /// 到着と次の出発が同時刻なら許すこと（乗り継ぎ 0 分は非現実的だが規則としては成立）
    @Test
    def testAcceptsBackToBackMovements(): Unit \ Assert =
        let legs =
            movement("JPOSA", "JPTYO", "2026-11-01T10:00", "2026-11-02T09:00") ::
            movement("JPTYO", "USLAX", "2026-11-02T09:00", "2026-11-15T08:00") :: Nil;
        Assert.assertEq(expected = None, errOf(register(aVoyageNumber(), legs, general())))

    ///
    /// 区間の順序番号が 1 始まりの連番へ正規化されること。
    ///
    /// 入力の順序をそのまま信じると、`seq_number` が飛んだり重複したりする。
    /// **保存する値は集約が決める**。
    ///
    @Test
    def testNormalizesSequenceNumbers(): Unit \ Assert =
        match register(aVoyageNumber(), viaTokyo(), general()) {
            case Err(e) => Assert.fail("登録に失敗した: ${e}")
            case Ok(v)  =>
                let seqs = movementsOf(v) |> List.map(seqNumberOf);
                Assert.assertEq(expected = 1 :: 2 :: Nil, seqs)
        }

    // ---- 航程の端点 ----

    ///
    /// 航海全体の出発地・仕向地を取り出せること。
    ///
    /// 検索（US07）は端点で絞る。**区間の一覧から毎回計算させない**。
    ///
    @Test
    def testExposesOriginAndDestination(): Unit \ Assert =
        match register(aVoyageNumber(), viaTokyo(), general()) {
            case Err(e) => Assert.fail("登録に失敗した: ${e}")
            case Ok(v)  =>
                Assert.assertEq(expected = "JPOSA", originOf(v));
                Assert.assertEq(expected = "USLAX", destinationOf(v))
        }

    // ---- RT-7: 対応貨物種別 ----

    ///
    /// 対応する貨物種別が 1 つ以上あること。
    ///
    /// 0 件だと**何も運べない航海**になり、検索結果に出ても選べない。
    ///
    @Test
    def testRequiresAtLeastOneCapability(): Unit \ Assert =
        Assert.assertEq(expected = Some(VoyageError.CapabilitiesAreEmpty),
                        errOf(register(aVoyageNumber(), directLeg() :: Nil, Nil)))

    @Test
    def testSupportsDeclaredCapabilities(): Unit \ Assert =
        match register(aVoyageNumber(), directLeg() :: Nil,
                       CargoCapability.General :: CargoCapability.Refrigerated :: Nil) {
            case Err(e) => Assert.fail("登録に失敗した: ${e}")
            case Ok(v)  =>
                Assert.assertTrue(supports(v, CargoCapability.General));
                Assert.assertTrue(supports(v, CargoCapability.Refrigerated));
                Assert.assertTrue(not supports(v, CargoCapability.Hazardous))
        }

    /// 重複した種別を 1 つにまとめること
    @Test
    def testDeduplicatesCapabilities(): Unit \ Assert =
        match register(aVoyageNumber(), directLeg() :: Nil,
                       CargoCapability.General :: CargoCapability.General :: Nil) {
            case Err(e) => Assert.fail("登録に失敗した: ${e}")
            case Ok(v)  => Assert.assertEq(expected = 1, List.length(capabilitiesOf(v)))
        }

    // ---- 永続化値との対応 ----

    @Test
    def testCapabilityRoundTrip(): Unit \ Assert =
        let all = CargoCapability.General :: CargoCapability.Hazardous ::
                  CargoCapability.Refrigerated :: Nil;
        Assert.assertTrue(all |> List.forAll(c ->
            RoutingModel.capabilityFromPersisted(RoutingModel.capabilityToPersisted(c)) == Some(c)))

    ///
    /// 未知の永続化値を既定値へ倒さないこと。
    ///
    /// 危険物対応でない航海が「一般貨物対応」として読まれると、
    /// 逆に危険物対応の航海が候補から漏れる。どちらも業務上の誤りになる。
    ///
    @Test
    def testRejectsUnknownPersistedCapability(): Unit \ Assert =
        Assert.assertEq(expected = None, RoutingModel.capabilityFromPersisted("LIVESTOCK"))

    ///
    /// 種別の集合が**区切り文字で囲まれた形**で永続化されること。
    ///
    /// `,GENERAL,` のように前後に区切りを置く。囲まないと
    /// `LIKE '%GENERAL%'` が `GENERAL_CARGO` にも一致する（誤検出）。
    ///
    @Test
    def testEncodesCapabilitiesWithDelimiters(): Unit \ Assert =
        let encoded = RoutingModel.capabilitiesToPersisted(
            CargoCapability.General :: CargoCapability.Refrigerated :: Nil);
        Assert.assertTrue(String.startsWith(prefix = ",", encoded));
        Assert.assertTrue(String.endsWith(suffix = ",", encoded));
        Assert.assertTrue(String.contains(substr = ",GENERAL,", encoded));
        Assert.assertTrue(String.contains(substr = ",REFRIGERATED,", encoded))

    @Test
    def testCapabilitiesRoundTrip(): Unit \ Assert =
        let caps = CargoCapability.General :: CargoCapability.Hazardous :: Nil;
        let decoded = RoutingModel.capabilitiesFromPersisted(
            RoutingModel.capabilitiesToPersisted(caps));
        Assert.assertEq(expected = 2, List.length(decoded))

    /// 読めない値が混ざっていても、読める分だけを返すこと
    @Test
    def testIgnoresUnknownValuesWhenDecoding(): Unit \ Assert =
        let decoded = RoutingModel.capabilitiesFromPersisted(",GENERAL,LIVESTOCK,");
        Assert.assertEq(expected = CargoCapability.General :: Nil, decoded)

    // ---- 版（楽観的ロックの指紋。IT8・TS14。IT6 レビュー M8） ----

    def versionOfOrFail(r: Result[VoyageError, Voyage]): String = match r {
        case Ok(v)  => RoutingModel.versionOf(v)
        case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
    }

    ///
    /// **同じ内容なら同じ版**になること。
    ///
    /// 更新日時を使わない設計の理由そのものである。「内容は同じだが
    /// 保存し直した」場合に食い違いとして扱うと、利用者は理由の分からない
    /// 「他の人が更新しました」を見せられる。
    ///
    @Test
    def testSameContentYieldsSameVersion(): Unit \ Assert =
        Assert.assertEq(
            expected = versionOfOrFail(register(aVoyageNumber(), viaTokyo(), general())),
            versionOfOrFail(register(aVoyageNumber(), viaTokyo(), general())))

    ///
    /// **航海番号が違えば版も違うこと**（IT6 レビュー M8 の後半）。
    ///
    /// IT6 の実装は航海番号を版に含めておらず、`V-0100S` と `V-0200S` の
    /// 内容が同じなら版が一致した。現在の呼び出し経路では航海番号で引いた後に
    /// 照合するため実害は出ないが、**版の一致が「同じ航海の同じ内容」を
    /// 意味しない**状態だった。呼び出し順に依存した安全性は、経路が増えたときに崩れる。
    ///
    /// この式が偽になる入力: 版の材料に航海番号を含めない実装。
    ///
    @Test
    def testDifferentVoyageNumberYieldsDifferentVersion(): Unit \ Assert =
        Assert.assertTrue(
            versionOfOrFail(register("V-0100S", viaTokyo(), general())) !=
            versionOfOrFail(register("V-0200S", viaTokyo(), general())))

    ///
    /// **航程が違えば版も違うこと**。
    ///
    /// 版が拾い損ねる属性があると、その属性だけを変えた更新が
    /// 「変更なし」と見なされ、同時編集で**片方の変更が黙って消える**。
    ///
    @Test
    def testDifferentMovementsYieldDifferentVersion(): Unit \ Assert =
        Assert.assertTrue(
            versionOfOrFail(register(aVoyageNumber(), viaTokyo(), general())) !=
            versionOfOrFail(register(aVoyageNumber(), directLeg() :: Nil, general())))

    /// 対応貨物種別が違えば版も違うこと
    @Test
    def testDifferentCapabilitiesYieldDifferentVersion(): Unit \ Assert =
        Assert.assertTrue(
            versionOfOrFail(register(aVoyageNumber(), viaTokyo(), general())) !=
            versionOfOrFail(register(aVoyageNumber(), viaTokyo(),
                CargoCapability.General :: CargoCapability.Refrigerated :: Nil)))

    /// 船名が違えば版も違うこと
    @Test
    def testDifferentVesselNameYieldsDifferentVersion(): Unit \ Assert =
        Assert.assertTrue(
            versionOfOrFail(register(aVoyageNumber(), viaTokyo(), general())) !=
            versionOfOrFail(registerVoyage(aVoyageNumber(), "SS Kaede", "Sakura Line",
                                           viaTokyo(), general())))

    /// 船社名が違えば版も違うこと
    @Test
    def testDifferentCarrierNameYieldsDifferentVersion(): Unit \ Assert =
        Assert.assertTrue(
            versionOfOrFail(register(aVoyageNumber(), viaTokyo(), general())) !=
            versionOfOrFail(registerVoyage(aVoyageNumber(), "SS Sakura", "Kaede Line",
                                           viaTokyo(), general())))

    ///
    /// **対応貨物種別を 1 行で表す**こと（IT6 レビュー M7）。
    ///
    /// 航海一覧と変更内容の差分表示が同じ組み立てを別々に持っていた。
    /// 区切り文字をここで固定し、**画面によって形が変わらない**ようにする。
    ///
    @Test
    def testJoinsCapabilitiesForDisplay(): Unit \ Assert =
        match register(aVoyageNumber(), viaTokyo(),
                       CargoCapability.General :: CargoCapability.Refrigerated :: Nil) {
            case Ok(v)  => Assert.assertEq(expected = "一般貨物・冷凍・冷蔵貨物",
                                           RoutingModel.capabilitiesText(v))
            case Err(e) => Assert.fail("フィクスチャの生成に失敗した: ${e}")
        }
}
