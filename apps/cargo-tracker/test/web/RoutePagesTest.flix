///
/// 経路割り当て画面の描画テスト（US08）。
///
/// **新しい画面を足したら、既存 BC のテスト構成を写す**（IT6 ふりかえり Try T7）。
/// IT6 は `VoyagePages`（455 行）に描画テストを 1 件も書かず、エスケープの検証が
/// どのレベルにも無い状態で 1 イテレーション動いていた（P5）。
///
mod RoutePagesTest {
    use RoutePages.{renderAssign, renderNotAvailable, EmptyReason}
    use RoutingBookingRouteRequest.{RouteRequest, routeRequest}
    use RoutingModel.{CargoCapability, reconstruct, reconstructMovement}
    use RoutingRouteFinder.{RouteCandidate, findCandidates}
    use RoutingRouteSpec.routeSpec
    use SharedSecurity.{Role, principal, Principal}

    def router(): Option[Principal] = Some(principal(1i64, "経路太郎", Role.Router))

    def request(): RouteRequest =
        routeRequest("BK-1001", "JPOSA", "USLAX", "2026-04-15", "一般貨物",
                     CargoCapability.General)

    /// 直行 1 件 + 積み替え 1 件
    def candidates(): List[RouteCandidate] =
        let direct =
            reconstruct("V-DIRECT", "SAKURA MARU", "NYK", CargoCapability.General :: Nil,
                reconstructMovement("JPOSA", "USLAX",
                    "2026-04-01T18:00", "2026-04-14T08:00", 1) :: Nil);
        let feeder =
            reconstruct("V-FEEDER", "KOMAKI", "ONE", CargoCapability.General :: Nil,
                reconstructMovement("JPOSA", "CNSHA",
                    "2026-04-02T08:00", "2026-04-04T08:00", 1) :: Nil);
        let mainLine =
            reconstruct("V-MAIN", "PACIFIC", "ONE", CargoCapability.General :: Nil,
                reconstructMovement("CNSHA", "USLAX",
                    "2026-04-05T00:00", "2026-04-13T06:00", 1) :: Nil);
        match routeSpec("JPOSA", "USLAX", "2026-01-01", "2026-04-15", CargoCapability.General) {
            case Ok(s)  => findCandidates(s, direct :: feeder :: mainLine :: Nil)
            case Err(_) => Nil
        }

    def assignHtml(): String =
        renderAssign(router(), request(), candidates(),
                     EmptyReason.NoRouteSatisfiesRequest, Some("token"))

    // ---- 依頼の条件（US07 受入基準 1） ----

    ///
    /// **予約番号から出発地・仕向地・期限・貨物仕様を確認できること**。
    ///
    /// IT6 で US07 の受入基準 1 が未充足のまま残っていた（引き継ぎ M5）。
    /// 経路設計者は条件を見ずに経路を選べない。
    ///
    /// この式が偽になる入力: 候補の表だけを描画し、依頼の条件を出さない実装。
    ///
    @Test
    def testShowsRequestConditions(): Unit \ Assert =
        let html = assignHtml();
        let expected = "BK-1001" :: "JPOSA" :: "USLAX" :: "2026-04-15" :: "一般貨物" :: Nil;
        let missing = expected |> List.filter(v -> not String.contains(substr = v, html));
        Assert.assertEq(expected = Nil, missing)

    // ---- 候補 ----

    ///
    /// 候補ごとに航路番号・経由港・出発予定・到着予定・所要日数が並ぶこと（受入基準 3）。
    ///
    /// この式が偽になる入力: 航路番号だけを並べる実装。
    ///
    @Test
    def testShowsCandidateColumns(): Unit \ Assert =
        let html = assignHtml();
        let expected = "航路番号" :: "経由港" :: "出発予定" :: "到着予定" ::
                       "所要日数" :: "V-DIRECT" :: "CNSHA" :: "11 日" :: Nil;
        let missing = expected |> List.filter(v -> not String.contains(substr = v, html));
        Assert.assertEq(expected = Nil, missing)

    ///
    /// **費用の列を出さないこと**。
    ///
    /// 運賃モデルは US01（見積・IT8）で定義される。値の無い列を出せば、
    /// そこに入る数値は嘘になる。受入基準 3 の部分未充足として明示的に選んだ。
    ///
    /// この式が偽になる入力: 「費用」列を 0 円や「-」で埋める実装。
    ///
    @Test
    def testDoesNotShowCostColumn(): Unit \ Assert =
        Assert.assertTrue(not String.contains(substr = "費用", assignHtml()))

    ///
    /// 直行便が最初の行に来ること（受入基準 5）。
    ///
    /// この式が偽になる入力: 到着順に並べる実装（V-FEEDER が 4/13 着で先に来る）。
    ///
    @Test
    def testDirectCandidateAppearsFirst(): Unit \ Assert =
        let html = assignHtml();
        match (String.indexOf(substr = "V-DIRECT", html),
               String.indexOf(substr = "V-FEEDER", html)) {
            case (Some(d), Some(f)) => Assert.assertTrue(d < f)
            case _                  => Assert.fail("候補が描画されていない")
        }

    ///
    /// 直行便の経由港を空欄にせず「直行」と書くこと。
    ///
    /// 空欄は「経由港が無い」のか「取得できなかった」のか読めない。
    ///
    /// この式が偽になる入力: `transitPortsOf` が空のとき何も出さない実装。
    ///
    @Test
    def testDirectCandidateShowsChokkou(): Unit \ Assert =
        Assert.assertTrue(String.contains(substr = "直行", assignHtml()))

    ///
    /// **期限当日に着く候補に注意を添えること**。
    ///
    /// 期限内ではあるが余裕が無い。遅延が 1 日でも出れば間に合わない。
    ///
    /// この式が偽になる入力: 到着時刻をそのまま出すだけの実装。
    ///
    @Test
    def testMarksArrivalOnDeadlineDay(): Unit \ Assert =
        let onDeadline =
            reconstruct("V-ONTIME", "GIRIGIRI", "NYK", CargoCapability.General :: Nil,
                reconstructMovement("JPOSA", "USLAX",
                    "2026-04-05T09:00", "2026-04-15T18:00", 1) :: Nil);
        let cs = match routeSpec("JPOSA", "USLAX", "2026-01-01", "2026-04-15", CargoCapability.General) {
            case Ok(s)  => findCandidates(s, onDeadline :: Nil)
            case Err(_) => Nil
        };
        let html = renderAssign(router(), request(), cs,
                                EmptyReason.NoRouteSatisfiesRequest, Some("token"));
        Assert.assertTrue(String.contains(substr = "期限当日", html))

    ///
    /// **選択がまだできないことを明示すること**。
    ///
    /// 候補を並べておいて押せる要素が無いと、利用者は壊れていると受け取る。
    /// 未実装のナビ項目を「準備中」と示したのと同じ判断である。
    ///
    /// この式が偽になる入力: 候補の表だけを出して何も案内しない実装。
    ///
    @Test
    def testExplainsSelectionIsNotYetAvailable(): Unit \ Assert =
        Assert.assertTrue(String.contains(substr = "次のイテレーション", assignHtml()))

    // ---- 候補 0 件 ----

    ///
    /// **「航海が未登録」と「条件に合う経路が無い」を書き分けること**。
    ///
    /// 同じ文言にすると、経路設計者は「航海を登録すべきか」「条件を緩めるべきか」を
    /// 判断できない（観点表の軸 3）。
    ///
    /// この式が偽になる入力: 両方に「経路がありません」とだけ出す実装。
    ///
    @Test
    def testDistinguishesEmptyReasons(): Unit \ Assert =
        let noVoyages = renderAssign(router(), request(), Nil,
                                     EmptyReason.NoVoyagesRegistered, Some("token"));
        let noRoute = renderAssign(router(), request(), Nil,
                                   EmptyReason.NoRouteSatisfiesRequest, Some("token"));
        Assert.assertTrue(String.contains(substr = "航海スケジュールが登録されていません", noVoyages));
        Assert.assertTrue(not String.contains(substr = "航海スケジュールが登録されていません", noRoute));
        Assert.assertTrue(String.contains(substr = "条件を満たす経路がありません", noRoute))

    ///
    /// 候補 0 件でも依頼の条件を出すこと。
    ///
    /// 「どの条件で 0 件だったか」が分からなければ、何を緩めるか判断できない。
    ///
    /// この式が偽になる入力: 0 件のときに条件の表を描画しない実装。
    ///
    @Test
    def testShowsConditionsEvenWhenEmpty(): Unit \ Assert =
        let html = renderAssign(router(), request(), Nil,
                                EmptyReason.NoRouteSatisfiesRequest, Some("token"));
        Assert.assertTrue(String.contains(substr = "JPOSA", html) and
                          String.contains(substr = "2026-04-15", html))

    ///
    /// **0 件でも行き止まりにしないこと**（受入基準 6）。
    ///
    /// 条件を変えての再算出（US10）は将来リリースのため、
    /// 航路管理と予約詳細（営業への相談）の 2 つの導線を置く。
    ///
    /// この式が偽になる入力: 文言だけ出してリンクを置かない実装。
    ///
    @Test
    def testEmptyResultOffersNextActions(): Unit \ Assert =
        let html = renderAssign(router(), request(), Nil,
                                EmptyReason.NoRouteSatisfiesRequest, Some("token"));
        Assert.assertTrue(String.contains(substr = "href=\"/voyages\"", html));
        Assert.assertTrue(String.contains(substr = "href=\"/bookings/BK-1001\"", html))

    // ---- 対象外 ----

    ///
    /// **「存在しない」と「引き渡されていない」を書き分けないこと**。
    ///
    /// 経路設計者にとってはどちらも「今は作業できない」であり、
    /// 書き分けると**存在の有無が漏れる**（観点表の軸 6）。
    ///
    /// この式が偽になる入力: 「予約が見つかりません」と
    /// 「まだ引き渡されていません」を出し分ける実装。
    ///
    @Test
    def testNotAvailableDoesNotLeakExistence(): Unit \ Assert =
        let html = renderNotAvailable(router(), "BK-9999", Some("token"));
        Assert.assertTrue(String.contains(substr = "経路設計の対象ではありません", html));
        Assert.assertTrue(not String.contains(substr = "見つかりません", html))

    ///
    /// 対象外でも一覧へ戻る導線があること。
    ///
    /// この式が偽になる入力: メッセージだけを返す実装。
    ///
    @Test
    def testNotAvailableOffersWayBack(): Unit \ Assert =
        Assert.assertTrue(String.contains(substr = "href=\"/bookings\"",
                                          renderNotAvailable(router(), "BK-9999", Some("token"))))

    // ---- エスケープ ----

    ///
    /// **予約番号がエスケープされること**。
    ///
    /// `bookingId` はパス変数として外から来る。IT6 は新しい BC を立ち上げた際に
    /// 既存の層のテスト構成を写さず、エスケープの検証がどこにも無いまま
    /// 1 イテレーション動いていた（P5）。
    ///
    /// この式が偽になる入力: `RawUnsafe` で予約番号を埋め込む実装。
    ///
    @Test
    def testEscapesBookingIdInNotAvailable(): Unit \ Assert =
        let html = renderNotAvailable(router(), "<script>alert(1)</script>", Some("token"));
        Assert.assertTrue(not String.contains(substr = "<script>alert(1)</script>", html))

    ///
    /// 依頼の値がエスケープされること。
    ///
    /// DB から読んだ値も信用しない。`cargo_type` の表示名は ACL が決めるが、
    /// 出発地・仕向地は DB の値がそのまま流れる。
    ///
    /// この式が偽になる入力: 条件の表を `RawUnsafe` で組み立てる実装。
    ///
    @Test
    def testEscapesRequestFields(): Unit \ Assert =
        let evil = routeRequest("BK-1", "<img src=x onerror=alert(1)>", "USLAX",
                                "2026-04-15", "一般貨物", CargoCapability.General);
        let html = renderAssign(router(), evil, Nil,
                                EmptyReason.NoRouteSatisfiesRequest, Some("token"));
        Assert.assertTrue(not String.contains(substr = "<img src=x", html))
}
