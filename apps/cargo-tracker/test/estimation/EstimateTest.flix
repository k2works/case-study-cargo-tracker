///
/// `Estimate` 集約と値オブジェクトの単体テスト（US01・IT8）。
///
/// 対応するビジネスルールは [ドメインモデル設計](../../../../docs/design/domain-model.md)
/// の Estimation Context（`business_rule_traceability.md` の ES-1〜ES-5）。
///
/// 効果を一切要求しない。DB もサーバも起動せず、ビジネスルールだけを固定する。
///
mod EstimateTest {
    use EstimationModel.{Estimate, CargoKind, EstimateStatus, EstimateError,
                         createEstimate, replaceCandidates, routeCandidate}
    use EstimationModel.{estimateIdOf, originOf, destinationOf, arrivalDeadlineOf,
                         cargoKindOf, weightGramsOf, candidatesOf, statusOf}

    def anId(): String = "aabbccdd-1111-2222-3333-444444444444"
    def today(): String = "2026-08-04"

    def create(origin: String, destination: String, deadline: String,
               weightGrams: Int64): Result[EstimateError, Estimate] =
        createEstimate(anId(), origin, destination, deadline,
                       CargoKind.General, weightGrams, today())

    def errOf(r: Result[EstimateError, t]): Option[EstimateError] = match r {
        case Err(e) => Some(e)
        case Ok(_)  => None
    }

    def okOf(r: Result[EstimateError, Estimate]): Estimate = match r {
        case Ok(e)  => e
        case Err(x) => bug!("フィクスチャの生成に失敗した: ${x}")
    }

    // ---- ES-1: 必須項目 ----

    ///
    /// 見積は出発地・仕向地・期限・貨物種別・重量を保持すること（ES-1）。
    ///
    /// **識別子は採番済みの値をそのまま持つ**。表示のときだけ `EST-` を
    /// 付ける形にすると、URL には UUID が現れ、利用者が電話口で読み上げる
    /// 番号と一致しなくなる（受入テストを書く過程で気付いた）。
    ///
    @Test
    def testHoldsAllRequiredFields(): Unit \ Assert =
        let e = okOf(create("JPOSA", "USLAX", "2026-12-31", 1200000i64));
        Assert.assertEq(expected = anId(), estimateIdOf(e));
        Assert.assertEq(expected = "JPOSA", originOf(e));
        Assert.assertEq(expected = "USLAX", destinationOf(e));
        Assert.assertEq(expected = "2026-12-31", arrivalDeadlineOf(e));
        Assert.assertTrue(cargoKindOf(e) == CargoKind.General);
        Assert.assertEq(expected = 1200000i64, weightGramsOf(e))

    /// 出発地が空なら成立しないこと（ES-1）
    @Test
    def testRequiresOrigin(): Unit \ Assert =
        Assert.assertEq(expected = Some(EstimateError.OriginIsRequired),
                        errOf(create("", "USLAX", "2026-12-31", 1200000i64)))

    /// 仕向地が空なら成立しないこと（ES-1）
    @Test
    def testRequiresDestination(): Unit \ Assert =
        Assert.assertEq(expected = Some(EstimateError.DestinationIsRequired),
                        errOf(create("JPOSA", "", "2026-12-31", 1200000i64)))

    /// 期限が空なら成立しないこと（ES-1）
    @Test
    def testRequiresArrivalDeadline(): Unit \ Assert =
        Assert.assertEq(expected = Some(EstimateError.ArrivalDeadlineIsRequired),
                        errOf(create("JPOSA", "USLAX", "", 1200000i64)))

    ///
    /// **UN/LOCODE として成立しない値を弾くこと**（ES-1）。
    ///
    /// `Char.isLetter` を使うと全角文字を通す。IT4 で `日本大阪港` が
    /// UN/LOCODE として通り、保存時に外部キー違反となって
    /// **利用者には応答が返らなかった**。
    ///
    @Test
    def testRejectsMalformedLocationCode(): Unit \ Assert =
        Assert.assertEq(expected = Some(EstimateError.LocationCodeIsMalformed),
                        errOf(create("日本大阪港", "USLAX", "2026-12-31", 1200000i64)))

    /// 期限の形式が違えば弾くこと（ES-1）
    @Test
    def testRejectsMalformedDeadline(): Unit \ Assert =
        Assert.assertEq(expected = Some(EstimateError.ArrivalDeadlineIsMalformed),
                        errOf(create("JPOSA", "USLAX", "2026/12/31", 1200000i64)))

    ///
    /// **過去の期限を弾き、当日は通すこと**（ES-1）。
    ///
    /// 当日を弾くと、今日中に着く便がある引き合いに答えられない
    /// （IT7 で確立した期限の境界と同じ）。
    ///
    /// この式が偽になる入力: `deadline <= today` で弾く実装。
    ///
    @Test
    def testRejectsPastDeadlineButAcceptsToday(): Unit \ Assert =
        Assert.assertEq(expected = Some(EstimateError.ArrivalDeadlineIsInPast),
                        errOf(create("JPOSA", "USLAX", "2026-08-03", 1200000i64)));
        Assert.assertEq(expected = None,
                        errOf(create("JPOSA", "USLAX", today(), 1200000i64)))

    // ---- ES-2: 出発地と仕向地は異なる ----

    ///
    /// 同一地点への見積は作れないこと（ES-2）。
    ///
    /// この式が偽になる入力: 大文字小文字を揃えずに比較する実装
    /// （`jposa` と `JPOSA` が別物として通る）。
    ///
    @Test
    def testRejectsSameOriginAndDestination(): Unit \ Assert =
        Assert.assertEq(expected = Some(EstimateError.OriginAndDestinationAreSame),
                        errOf(create("JPOSA", "jposa", "2026-12-31", 1200000i64)))

    // ---- ES-3: 重量は正 ----

    /// 重量が 0 なら成立しないこと（ES-3）
    @Test
    def testRejectsZeroWeight(): Unit \ Assert =
        Assert.assertEq(expected = Some(EstimateError.WeightIsNotPositive),
                        errOf(create("JPOSA", "USLAX", "2026-12-31", 0i64)))

    /// 重量が負なら成立しないこと（ES-3）
    @Test
    def testRejectsNegativeWeight(): Unit \ Assert =
        Assert.assertEq(expected = Some(EstimateError.WeightIsNotPositive),
                        errOf(create("JPOSA", "USLAX", "2026-12-31", -1i64)))

    // ---- ES-4: ルート候補 ----

    ///
    /// **候補は置き換えること**（ES-4）。
    ///
    /// 再算出は「その時点の航海で引き直す」ことである。足す形にすると、
    /// 古い候補が混ざり、**もう存在しない便を提示しうる**。
    ///
    /// この式が偽になる入力: `candidates ::: newCandidates` で足す実装。
    ///
    @Test
    def testReplacesCandidatesInsteadOfAppending(): Unit \ Assert =
        let e = okOf(create("JPOSA", "USLAX", "2026-12-31", 1200000i64));
        let first = routeCandidate("V-0001" :: Nil, Nil, 10, SharedMoney.ofYen(100i64));
        let second = routeCandidate("V-0002" :: Nil, Nil, 12, SharedMoney.ofYen(200i64));
        let replaced = replaceCandidates(replaceCandidates(e, first :: Nil), second :: Nil);
        Assert.assertEq(expected = "V-0002" :: Nil,
            candidatesOf(replaced)
                |> List.flatMap(EstimationModel.candidateVoyageNumbersOf))

    ///
    /// **積替のある候補は航海番号と経由港を複数持てること**（ES-4）。
    ///
    /// データモデルの `transit_port` は 1 列だが、**列に合わせてモデルを削らない**。
    /// 寄港地が 2 つある候補を表現できない形にすると、
    /// 画面に出せる候補が実際より少なくなる。
    ///
    @Test
    def testCandidateHoldsMultipleVoyagesAndPorts(): Unit \ Assert =
        let c = routeCandidate("V-0001" :: "V-0002" :: Nil,
                               "JPTYO" :: "CNSHA" :: Nil, 15, SharedMoney.ofYen(300i64));
        Assert.assertEq(expected = 2,
                        List.length(EstimationModel.candidateVoyageNumbersOf(c)));
        Assert.assertEq(expected = "JPTYO" :: "CNSHA" :: Nil,
                        EstimationModel.candidateTransitPortsOf(c))

    // ---- ES-5: 既定の状態 ----

    /// 作成時の状態は「作成済」であること（ES-5）
    @Test
    def testStartsAsCreated(): Unit \ Assert =
        let e = okOf(create("JPOSA", "USLAX", "2026-12-31", 1200000i64));
        Assert.assertTrue(statusOf(e) == EstimateStatus.Created)

    // ---- 識別子・永続化値 ----

    /// 見積番号は `EST-` + 大文字 8 桁になること
    @Test
    def testGeneratesEstimateNumber(): Unit \ Assert =
        Assert.assertEq(expected = "EST-AABBCCDD",
                        EstimationModel.generateEstimateId(anId()))

    ///
    /// **未知の貨物種別を既定値へ倒さないこと**。
    ///
    /// 倒すと、危険物の見積が一般貨物として読み出され、**割増の付かない金額**が
    /// 「保存された見積」として画面に出る。
    ///
    @Test
    def testDoesNotDefaultUnknownCargoKind(): Unit \ Assert =
        Assert.assertEq(expected = None, EstimationModel.cargoKindFromPersisted("LIVESTOCK"))

    /// 貨物種別が永続化値と往復すること
    @Test
    def testCargoKindRoundTrips(): Unit \ Assert =
        let roundTrip = k ->
            EstimationModel.cargoKindFromPersisted(EstimationModel.cargoKindToPersisted(k));
        Assert.assertTrue(EstimationModel.allCargoKinds()
            |> List.forAll(k -> roundTrip(k) == Some(k)))

    /// 見積状態が永続化値と往復すること
    @Test
    def testStatusRoundTrips(): Unit \ Assert =
        let roundTrip = s ->
            EstimationModel.statusFromPersisted(EstimationModel.statusToPersisted(s));
        Assert.assertTrue(roundTrip(EstimateStatus.Created) == Some(EstimateStatus.Created));
        Assert.assertTrue(roundTrip(EstimateStatus.Expired) == Some(EstimateStatus.Expired))
}
