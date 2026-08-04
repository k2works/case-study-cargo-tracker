///
/// 経路候補の算出（US08）のドメイン単体テスト。
///
/// **効果を一切要求しない**。航海の一覧と探索条件を渡し、候補の一覧を得るだけである。
/// DB も HTTP も要らないため、境界の組み合わせをここで尽くす
/// （[テスト戦略](../../../../docs/design/test_strategy.md) 3.1.1）。
///
/// 到達可能性の判定は Datalog（推移閉包）で行うが、それは `RouteFinder` の
/// 実装詳細である。本テストが見るのは**候補の集合と並び**だけであり、
/// 手段が変わってもテストは変わらない。
///
mod RouteFinderTest {
    use RoutingModel.{CargoCapability, Voyage, movement, reconstruct}
    use RoutingRouteFinder.{findCandidates, RouteCandidate}
    use RoutingRouteFinder.{voyageNumbersOf, transshipmentCountOf, arrivalTimeOf,
                            transitPortsOf, transitDaysOf}
    use RoutingRouteSpec.routeSpec

    // ---- フィクスチャ ----

    /// 直行便 JPOSA → USLAX（4/1 出航・4/14 08:00 着）
    def direct(): Voyage =
        reconstruct("V-DIRECT", "SAKURA MARU", "NYK", CargoCapability.General :: Nil,
            RoutingModel.reconstructMovement("JPOSA", "USLAX",
                "2026-04-01T18:00", "2026-04-14T08:00", 1) :: Nil)

    /// 寄港地 CNSHA を経由する航海（1 本の航海が 2 区間を持つ）
    def viaShanghai(): Voyage =
        reconstruct("V-VIA", "HIMAWARI", "MOL", CargoCapability.General :: Nil,
            RoutingModel.reconstructMovement("JPOSA", "CNSHA",
                "2026-04-03T10:00", "2026-04-05T09:00", 1) ::
            RoutingModel.reconstructMovement("CNSHA", "USLAX",
                "2026-04-05T20:00", "2026-04-16T12:00", 2) :: Nil)

    /// JPOSA → CNSHA（積み替えの前半）
    def feeder(): Voyage =
        reconstruct("V-FEEDER", "KOMAKI", "ONE", CargoCapability.General :: Nil,
            RoutingModel.reconstructMovement("JPOSA", "CNSHA",
                "2026-04-02T08:00", "2026-04-04T08:00", 1) :: Nil)

    /// CNSHA → USLAX（積み替えの後半。feeder の到着後に出航する）
    def mainLine(): Voyage =
        reconstruct("V-MAIN", "PACIFIC", "ONE", CargoCapability.General :: Nil,
            RoutingModel.reconstructMovement("CNSHA", "USLAX",
                "2026-04-05T00:00", "2026-04-13T06:00", 1) :: Nil)

    /// CNSHA → USLAX だが **feeder の到着より前に出航する**（接続が成立しない）
    def tooEarly(): Voyage =
        reconstruct("V-EARLY", "HAYABUSA", "ONE", CargoCapability.General :: Nil,
            RoutingModel.reconstructMovement("CNSHA", "USLAX",
                "2026-04-03T00:00", "2026-04-11T06:00", 1) :: Nil)

    /// 冷凍のみ対応の直行便
    def reeferOnly(): Voyage =
        reconstruct("V-REEFER", "SNOW", "K-LINE", CargoCapability.Refrigerated :: Nil,
            RoutingModel.reconstructMovement("JPOSA", "USLAX",
                "2026-04-01T09:00", "2026-04-12T09:00", 1) :: Nil)

    /// 期限当日の 18:00 に着く直行便（期限は日付のみで 00:00 を意味しうる）
    def arrivesOnDeadlineDay(): Voyage =
        reconstruct("V-ONTIME", "GIRIGIRI", "NYK", CargoCapability.General :: Nil,
            RoutingModel.reconstructMovement("JPOSA", "USLAX",
                "2026-04-05T09:00", "2026-04-15T18:00", 1) :: Nil)

    /// 期限の翌日に着く直行便
    def arrivesLate(): Voyage =
        reconstruct("V-LATE", "OSOI", "NYK", CargoCapability.General :: Nil,
            RoutingModel.reconstructMovement("JPOSA", "USLAX",
                "2026-04-05T09:00", "2026-04-16T00:01", 1) :: Nil)

    /// JPOSA → USLAX・期限 2026-04-15・一般貨物
    def spec(): RoutingRouteSpec.RouteSpec =
        unsafeSpec("JPOSA", "USLAX", "2026-04-15", CargoCapability.General)

    /// 出発可能日は既定で 2026-01-01（全フィクスチャの出発日より前）。
    /// **出発日の絞り込みそのものは専用のテストで確かめる**
    def unsafeSpec(origin: String, destination: String, deadline: String,
                   required: CargoCapability): RoutingRouteSpec.RouteSpec =
        unsafeSpecFrom(origin, destination, "2026-01-01", deadline, required)

    def unsafeSpecFrom(origin: String, destination: String, earliest: String,
                       deadline: String,
                       required: CargoCapability): RoutingRouteSpec.RouteSpec =
        match routeSpec(origin, destination, earliest, deadline, required) {
            case Ok(s)  => s
            case Err(_) => bug!("テストの探索条件が不正です")
        }

    // ---- 到達可能性 ----

    ///
    /// 直行便が候補になること。
    ///
    /// この式が偽になる入力: 直行便を返さない実装（区間が 1 本の航海を
    /// 「積み替えなし」として扱えていない場合）。
    ///
    @Test
    def testDirectVoyageIsCandidate(): Unit \ Assert =
        let found = findCandidates(spec(), direct() :: Nil);
        Assert.assertEq(expected = ("V-DIRECT" :: Nil) :: Nil, found |> List.map(voyageNumbersOf))

    ///
    /// **出発地・仕向地が航海の端点でなくても、寄港地を経由すれば候補になること**。
    ///
    /// IT6 の検索は端点としか一致せず、寄港地から積む貨物を探せなかった
    /// （レビュー M1・US07 受入基準 3）。US08 の到達可能性判定がこれを解消する。
    ///
    /// この式が偽になる入力: `viaShanghai` の 2 区間を連結せず、
    /// 「JPOSA → CNSHA」「CNSHA → USLAX」の 2 本として別々に扱う実装。
    ///
    @Test
    def testVoyageWithPortOfCallIsCandidate(): Unit \ Assert =
        // 期限を確保して**到達可能性だけ**を見る。`viaShanghai` は 4/16 着であり、
        // 既定の期限（4/15）のままだと期限で落ちる。**落ちた理由が
        // 「到達できない」なのか「間に合わない」なのか区別できない**テストになる
        let s = unsafeSpec("JPOSA", "USLAX", "2026-04-20", CargoCapability.General);
        let found = findCandidates(s, viaShanghai() :: Nil);
        Assert.assertEq(expected = ("V-VIA" :: Nil) :: Nil, found |> List.map(voyageNumbersOf))

    ///
    /// 中間地点を出発地とする探索でも見つかること（推移閉包の部分区間）。
    ///
    /// この式が偽になる入力: 航海の先頭区間の出発地としか照合しない実装。
    ///
    @Test
    def testSearchFromPortOfCall(): Unit \ Assert =
        let s = unsafeSpec("CNSHA", "USLAX", "2026-04-20", CargoCapability.General);
        let found = findCandidates(s, viaShanghai() :: Nil);
        Assert.assertEq(expected = ("V-VIA" :: Nil) :: Nil, found |> List.map(voyageNumbersOf))

    ///
    /// 到達できない仕向地では候補が 0 件になること。
    ///
    /// この式が偽になる入力: 仕向地を照合せず全航海を返す実装。
    ///
    @Test
    def testUnreachableDestinationYieldsNothing(): Unit \ Assert =
        let s = unsafeSpec("JPOSA", "DEHAM", "2026-05-31", CargoCapability.General);
        Assert.assertEq(expected = Nil,
                        findCandidates(s, direct() :: viaShanghai() :: Nil) |> List.map(voyageNumbersOf))

    // ---- 積み替え ----

    ///
    /// 2 本の航海をつないだ経路が候補になること。
    ///
    /// この式が偽になる入力: 単一航海の中だけで到達可能性を判定する実装。
    ///
    @Test
    def testTransshipmentIsCandidate(): Unit \ Assert =
        let found = findCandidates(spec(), feeder() :: mainLine() :: Nil);
        Assert.assertEq(expected = ("V-FEEDER" :: "V-MAIN" :: Nil) :: Nil,
                        found |> List.map(voyageNumbersOf));
        Assert.assertEq(expected = 1 :: Nil, found |> List.map(transshipmentCountOf))

    ///
    /// **接続時刻が逆転する組み合わせは候補にならないこと**。
    ///
    /// `tooEarly` は `feeder` が着く前（4/3）に出航する。到達可能性だけを見ると
    /// JPOSA → CNSHA → USLAX でつながるが、**その船には乗れない**。
    ///
    /// この式が偽になる入力: 時刻を見ずに港の接続だけで判定する実装。
    /// Datalog の推移閉包は港しか見ないため、これは必ず起きる。
    ///
    @Test
    def testInvertedConnectionIsRejected(): Unit \ Assert =
        Assert.assertEq(expected = Nil,
                        findCandidates(spec(), feeder() :: tooEarly() :: Nil) |> List.map(voyageNumbersOf))

    ///
    /// **到着と出発が同時刻の接続は成立すること**。
    ///
    /// `Voyage` のビジネスルール 6（出発時刻は到着時刻以前。同一時刻を許す）と
    /// 揃える。最低接続時間は業務ルールとして未定義であり、勝手に決めない。
    ///
    /// この式が偽になる入力: 接続の判定を `<`（真に前）で書いた実装。
    ///
    @Test
    def testSimultaneousConnectionIsAccepted(): Unit \ Assert =
        let sameInstant =
            reconstruct("V-SAME", "TSUGI", "ONE", CargoCapability.General :: Nil,
                RoutingModel.reconstructMovement("CNSHA", "USLAX",
                    "2026-04-04T08:00", "2026-04-13T06:00", 1) :: Nil);
        let found = findCandidates(spec(), feeder() :: sameInstant :: Nil);
        Assert.assertEq(expected = ("V-FEEDER" :: "V-SAME" :: Nil) :: Nil,
                        found |> List.map(voyageNumbersOf))

    ///
    /// 同じ航海を 2 度使う経路を作らないこと。
    ///
    /// この式が偽になる入力: 訪問済みの航海を除外しない探索。
    /// 港の推移閉包は循環を許すため、除外しないと無限に伸びる。
    ///
    @Test
    def testDoesNotReuseSameVoyage(): Unit \ Assert =
        let loop =
            reconstruct("V-LOOP", "MEGURU", "ONE", CargoCapability.General :: Nil,
                RoutingModel.reconstructMovement("JPOSA", "CNSHA",
                    "2026-04-01T00:00", "2026-04-02T00:00", 1) ::
                RoutingModel.reconstructMovement("CNSHA", "JPOSA",
                    "2026-04-02T06:00", "2026-04-03T00:00", 2) :: Nil);
        let found = findCandidates(spec(), loop :: mainLine() :: Nil);
        let reused = found |> List.filter(c ->
            let ns = voyageNumbersOf(c);
            List.length(ns) != List.length(List.distinct(ns)));
        Assert.assertEq(expected = Nil, reused |> List.map(voyageNumbersOf))

    ///
    /// **積み替えは 2 回まで**とすること。
    ///
    /// 推移閉包は経路数が指数的に増える。業務上も 3 回以上の積み替えは採らない。
    ///
    /// この式が偽になる入力: 上限を設けない探索（4 本目をつないだ候補が出る）。
    ///
    @Test
    def testTransshipmentIsLimitedToTwo(): Unit \ Assert =
        let leg = (num, depPort, arrPort, dep, arr) ->
            reconstruct(num, "SHIP", "ONE", CargoCapability.General :: Nil,
                RoutingModel.reconstructMovement(depPort, arrPort, dep, arr, 1) :: Nil);
        let fleet =
            leg("V-1", "JPOSA", "KRPUS", "2026-04-01T00:00", "2026-04-02T00:00") ::
            leg("V-2", "KRPUS", "CNSHA", "2026-04-03T00:00", "2026-04-04T00:00") ::
            leg("V-3", "CNSHA", "HKHKG", "2026-04-05T00:00", "2026-04-06T00:00") ::
            leg("V-4", "HKHKG", "USLAX", "2026-04-07T00:00", "2026-04-10T00:00") :: Nil;
        let found = findCandidates(spec(), fleet);
        // 4 本つなぐと積み替え 3 回になる。候補は 0 件が正しい
        Assert.assertEq(expected = Nil, found |> List.map(voyageNumbersOf))

    ///
    /// **積み替え 2 回はちょうど通ること**（境界の肯定側）。
    ///
    /// `testTransshipmentIsLimitedToTwo` は 3 回の否定しか見ておらず、
    /// **上限がずれて 1 回になっていても緑になる**（IT7 レビュー・tester 指摘）。
    ///
    /// この式が偽になる入力: `maxTransshipments` を 1 にした実装。
    ///
    @Test
    def testTwoTransshipmentsAreAllowed(): Unit \ Assert =
        let leg = (num, depPort, arrPort, dep, arr) ->
            reconstruct(num, "SHIP", "ONE", CargoCapability.General :: Nil,
                RoutingModel.reconstructMovement(depPort, arrPort, dep, arr, 1) :: Nil);
        let fleet =
            leg("V-A", "JPOSA", "KRPUS", "2026-04-01T00:00", "2026-04-02T00:00") ::
            leg("V-B", "KRPUS", "CNSHA", "2026-04-03T00:00", "2026-04-04T00:00") ::
            leg("V-C", "CNSHA", "USLAX", "2026-04-05T00:00", "2026-04-10T00:00") :: Nil;
        let found = findCandidates(spec(), fleet);
        Assert.assertEq(expected = ("V-A" :: "V-B" :: "V-C" :: Nil) :: Nil,
                        found |> List.map(voyageNumbersOf));
        Assert.assertEq(expected = 2 :: Nil, found |> List.map(transshipmentCountOf))

    // ---- 期限 ----

    ///
    /// **期限当日に時刻付きで着く経路を刈らないこと**。
    ///
    /// 期限は `DATE`（`2026-04-15` = 00:00）、到着は `TIMESTAMP`（`2026-04-15T18:00`）である。
    /// 素朴に文字列・時刻で比較すると 18:00 > 00:00 となり、**期限当日に着く便がすべて消える**。
    /// [テスト戦略](../../../../docs/design/test_strategy.md) 3.1.1 の「境界の落とし穴」。
    ///
    /// この式が偽になる入力: `arrivalTime <= deadline` を文字列比較で書いた実装
    /// （`"2026-04-15T18:00" <= "2026-04-15"` は偽）。
    ///
    @Test
    def testArrivalOnDeadlineDayIsAccepted(): Unit \ Assert =
        let found = findCandidates(spec(), arrivesOnDeadlineDay() :: Nil);
        Assert.assertEq(expected = ("V-ONTIME" :: Nil) :: Nil, found |> List.map(voyageNumbersOf))

    ///
    /// 期限翌日の到着は候補にならないこと。
    ///
    /// この式が偽になる入力: 日付単位に丸めるとき切り上げてしまう実装。
    ///
    @Test
    def testArrivalAfterDeadlineIsRejected(): Unit \ Assert =
        Assert.assertEq(expected = Nil,
                        findCandidates(spec(), arrivesLate() :: Nil) |> List.map(voyageNumbersOf))

    ///
    /// 積み替え経路でも**最終到着**で期限を判定すること。
    ///
    /// この式が偽になる入力: 先頭区間の到着で判定する実装。
    ///
    @Test
    def testDeadlineUsesFinalArrival(): Unit \ Assert =
        let s = unsafeSpec("JPOSA", "USLAX", "2026-04-12", CargoCapability.General);
        // V-FEEDER は 4/4 着だが V-MAIN の最終到着は 4/13。期限 4/12 を超える
        Assert.assertEq(expected = Nil,
                        findCandidates(s, feeder() :: mainLine() :: Nil) |> List.map(voyageNumbersOf))

    // ---- 出発日 ----

    ///
    /// **既に出港した便を候補にしないこと**。
    ///
    /// `connects` は先頭区間で無条件に真、`arrivesBy` は期限側しか見ない。
    /// したがって期限が先であれば**先月出港した便でも候補に並ぶ**。
    /// 経路設計者は一覧全体を「信用できない参考情報」に格下げする
    /// （IT7 レビュー・user-rep 指摘）。
    ///
    /// この式が偽になる入力: 出発日を見ない実装（IT7 の当初の実装）。
    ///
    @Test
    def testDepartedVoyageIsExcluded(): Unit \ Assert =
        // direct は 2026-04-01 出航。出発可能日を 4/02 にすると候補から消える
        let s = unsafeSpecFrom("JPOSA", "USLAX", "2026-04-02", "2026-04-20",
                               CargoCapability.General);
        Assert.assertEq(expected = Nil,
                        findCandidates(s, direct() :: Nil) |> List.map(voyageNumbersOf))

    ///
    /// **出発可能日当日に出る便は候補に残ること**（境界）。
    ///
    /// 出発可能日は日付（`Clock.today()`）、出発時刻は時刻付きである。
    /// 素朴に比較すると `"2026-04-01T18:00" >= "2026-04-01"` は真だが、
    /// 逆向き（`dateOf` を通さずに時刻付き同士を比べる形）では**今日の便が消える**。
    ///
    /// この式が偽になる入力: 出発時刻を切り詰めずに比較する実装のうち、
    /// 出発可能日側へ時刻を補って `T00:00` 以降とする代わりに
    /// `T23:59` 以降としてしまうもの。
    ///
    @Test
    def testVoyageDepartingTodayIsIncluded(): Unit \ Assert =
        let s = unsafeSpecFrom("JPOSA", "USLAX", "2026-04-01", "2026-04-20",
                               CargoCapability.General);
        Assert.assertEq(expected = ("V-DIRECT" :: Nil) :: Nil,
                        findCandidates(s, direct() :: Nil) |> List.map(voyageNumbersOf))

    ///
    /// **積み替えの後半区間は出発可能日より前でも構わないこと**。
    ///
    /// 絞るのは「乗れる区間」の出発であり、後半区間も出発可能日以降でなければ
    /// ならない——という誤解を防ぐためのテストではない。**逆である**:
    /// 現実装は全 hop に同じ条件をかけるため、**前半の到着より後に出る後半は
    /// 必然的に出発可能日以降**になる。ここで固定するのは
    /// 「余計に刈っていないこと」である。
    ///
    /// この式が偽になる入力: 先頭区間だけに条件をかけ、後半を無条件に通す実装
    /// （後半が過去でも候補になる）。
    ///
    @Test
    def testTransshipmentRespectsEarliestDeparture(): Unit \ Assert =
        let s = unsafeSpecFrom("JPOSA", "USLAX", "2026-04-02", "2026-04-20",
                               CargoCapability.General);
        Assert.assertEq(expected = ("V-FEEDER" :: "V-MAIN" :: Nil) :: Nil,
                        findCandidates(s, feeder() :: mainLine() :: Nil)
                            |> List.map(voyageNumbersOf))

    // ---- 対応貨物種別 ----

    ///
    /// 必要な種別に対応しない航海を候補にしないこと。
    ///
    /// この式が偽になる入力: `capabilities` を見ない実装。
    ///
    @Test
    def testVoyageWithoutCapabilityIsRejected(): Unit \ Assert =
        let s = unsafeSpec("JPOSA", "USLAX", "2026-04-15", CargoCapability.Hazardous);
        Assert.assertEq(expected = Nil,
                        findCandidates(s, direct() :: reeferOnly() :: Nil) |> List.map(voyageNumbersOf))

    ///
    /// **積み替え経路は全区間が対応していること**を要すること。
    ///
    /// 片方だけ冷凍対応でも、もう片方で解凍される。
    ///
    /// この式が偽になる入力: 先頭の航海だけ種別を見る実装。
    ///
    @Test
    def testEveryLegMustSupportCapability(): Unit \ Assert =
        let s = unsafeSpec("JPOSA", "USLAX", "2026-04-15", CargoCapability.Refrigerated);
        let reeferFeeder =
            reconstruct("V-RF", "KOORI", "ONE", CargoCapability.Refrigerated :: Nil,
                RoutingModel.reconstructMovement("JPOSA", "CNSHA",
                    "2026-04-02T08:00", "2026-04-04T08:00", 1) :: Nil);
        // mainLine は General のみ。後半で冷凍を維持できない
        Assert.assertEq(expected = Nil,
                        findCandidates(s, reeferFeeder :: mainLine() :: Nil) |> List.map(voyageNumbersOf))

    // ---- 推奨順 ----

    ///
    /// **直行便を最優先に並べること**（受入基準 5）。
    ///
    /// 到着が遅くても直行便を先に出す。積み替えの失敗リスクが無く、
    /// 経路設計者が最初に検討する対象だからである。到着順を優先すると、
    /// より早く着く積み替え便に直行便が沈む。
    ///
    /// この式が偽になる入力: 到着日時だけで整列する実装
    /// （`direct` は 4/14 着、`feeder + mainLine` は 4/13 着なので順序が入れ替わる）。
    ///
    @Test
    def testDirectVoyageComesFirst(): Unit \ Assert =
        let found = findCandidates(spec(), feeder() :: mainLine() :: direct() :: Nil);
        Assert.assertEq(expected = ("V-DIRECT" :: Nil) :: ("V-FEEDER" :: "V-MAIN" :: Nil) :: Nil,
                        found |> List.map(voyageNumbersOf))

    ///
    /// 積み替え回数が同じなら到着の早い順に並べること。
    ///
    /// この式が偽になる入力: 入力順のまま返す実装。
    ///
    @Test
    def testSameTransshipmentCountOrdersByArrival(): Unit \ Assert =
        // direct は 4/14 08:00 着、arrivesOnDeadlineDay は 4/15 18:00 着。
        // いずれも直行便なので到着順で並ぶ
        let found = findCandidates(spec(), arrivesOnDeadlineDay() :: direct() :: Nil);
        Assert.assertEq(expected = ("V-DIRECT" :: Nil) :: ("V-ONTIME" :: Nil) :: Nil,
                        found |> List.map(voyageNumbersOf))

    ///
    /// 積み替え回数も到着も同じなら航海番号の昇順に並べること。
    ///
    /// 並びを決定的にしないと、テストが環境によって落ちる。
    ///
    /// この式が偽になる入力: 第 3 の整列キーを持たない実装。
    ///
    @Test
    def testTiesAreBrokenByVoyageNumber(): Unit \ Assert =
        let twin = num ->
            reconstruct(num, "FUTAGO", "ONE", CargoCapability.General :: Nil,
                RoutingModel.reconstructMovement("JPOSA", "USLAX",
                    "2026-04-01T18:00", "2026-04-14T08:00", 1) :: Nil);
        let found = findCandidates(spec(), twin("V-B") :: twin("V-A") :: Nil);
        Assert.assertEq(expected = ("V-A" :: Nil) :: ("V-B" :: Nil) :: Nil,
                        found |> List.map(voyageNumbersOf))

    // ---- 候補の内容 ----

    ///
    /// 経由港が並ぶこと（受入基準 3）。
    ///
    /// この式が偽になる入力: 経由港を出さず出発地と仕向地だけを返す実装。
    ///
    @Test
    def testCandidateExposesTransitPorts(): Unit \ Assert =
        let found = findCandidates(spec(), feeder() :: mainLine() :: Nil);
        Assert.assertEq(expected = ("CNSHA" :: Nil) :: Nil, found |> List.map(transitPortsOf))

    ///
    /// 直行便の経由港は空であること。
    ///
    /// この式が偽になる入力: 出発地や仕向地を経由港に含める実装。
    ///
    @Test
    def testDirectCandidateHasNoTransitPort(): Unit \ Assert =
        let found = findCandidates(spec(), direct() :: Nil);
        Assert.assertEq(expected = (Nil: List[String]) :: Nil, found |> List.map(transitPortsOf))

    ///
    /// 所要日数が出航日から最終到着日までの日数になること（受入基準 3）。
    ///
    /// この式が偽になる入力: 区間ごとの日数を足す実装（待ち時間が落ちる）。
    /// `feeder`（4/2 出航）から `mainLine`（4/13 着）は 11 日だが、
    /// 区間ごとに足すと 2 + 8 = 10 日になる。
    ///
    @Test
    def testTransitDaysSpanTheWholeJourney(): Unit \ Assert =
        let found = findCandidates(spec(), feeder() :: mainLine() :: Nil);
        Assert.assertEq(expected = 11 :: Nil, found |> List.map(transitDaysOf))

    ///
    /// 最終到着時刻を保持すること。
    ///
    /// この式が偽になる入力: 先頭区間の到着を返す実装。
    ///
    @Test
    def testCandidateExposesFinalArrival(): Unit \ Assert =
        let found = findCandidates(spec(), feeder() :: mainLine() :: Nil);
        Assert.assertEq(expected = "2026-04-13T06:00" :: Nil, found |> List.map(arrivalTimeOf))

    ///
    /// 航海が 1 件も無いとき候補は 0 件であること。
    ///
    /// この式が偽になる入力: 空リストで例外を投げる実装。
    ///
    @Test
    def testNoVoyagesYieldsNoCandidates(): Unit \ Assert =
        Assert.assertEq(expected = Nil, findCandidates(spec(), Nil) |> List.map(voyageNumbersOf))
}
