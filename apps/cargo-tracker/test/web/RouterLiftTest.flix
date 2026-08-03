///
/// 効果の持ち上げ（`lift` / `touchAll`）が**副作用を起こさない**ことのテスト。
///
/// [ADR-0005](../../../../docs/adr/ADR-0005-single-routing-table.md) の設計は
/// 「効果は構文上使われていれば宣言でき、実行される必要はない」という性質に依存する。
/// この前提が崩れると、**全リクエストで `ShipperRepo.findAll()` や
/// `CargoRepo.findAll()` が余計に走る**（`ReadOnly` / `Write` の経路では本物の
/// ハンドラが供給されるため、テストは全て緑のまま画面だけが遅くなる）。
///
/// IT4 の実装中に実験として確認したがテストを残さず、レビューで
/// 「存在しないテストを根拠にコメントが書かれている」と指摘された。
/// **前提を守るのはコメントではなくテストである。**
///
mod RouterLiftTest {
    use AppComposition.{AppEff, appRoutes}
    use SharedHttp.{Request, Response, fullRequest, status}
    use SharedHttpRouter.{Route, TxMode, routeHandler, routeTxMode, routeKey}
    use SharedDbPool.{create, h2Config, close}

    ///
    /// `lift` を通したハンドラを 1 回実行し、**どの効果操作も呼ばれない**ことを確かめる。
    ///
    /// 全効果に呼び出し回数のカウンタを持つハンドラを適用する。`touchAll(false)` の
    /// 枝が実行されるようになれば、いずれかのカウンタが 1 以上になる。
    ///
    @Test
    def testLiftDoesNotInvokeEffectOperations(): Unit \ Assert + IO =
        // `Static` リージョンにするのは、効果ハンドラの入れ子（IO）と
        // リージョン効果が混ざると型が追えなくなるためである
        let calls = Ref.fresh(Static, 0);
        let ds = create(h2Config("lift_no_side_effects"));
        // `/health/live` は効果を要求しないハンドラ。`lift` が包んでいる
        let liveRoute = appRoutes(ds) |> List.find(r -> routeKey(r) == "GET /health/live");
        close(ds);
        match liveRoute {
            case None => Assert.fail("/health/live がルーティング表に見つからなかった")
            case Some(r) =>
                let res = runWithCounters(calls, () -> routeHandler(r)(sampleRequest()));
                Assert.assertEq(expected = 200, status(res));
                // **ここが本題**。lift が包んだ結果、効果操作が 1 度も走っていないこと
                Assert.assertEq(expected = 0, Ref.get(calls))
        }

    ///
    /// 全効果に「呼ばれたら数える」ハンドラを適用する。
    ///
    /// 本番の `runWithoutDb` が `bug!` で落とすのに対し、ここでは**落とさずに数える**。
    /// 落とすと「呼ばれた回数」が分からず、1 回目で止まってしまう。
    ///
    def runWithCounters(calls: Ref[Int32, Static], f: Unit -> a \ AppEff): a \ IO =
        let bump = () -> Ref.put(Ref.get(calls) + 1, calls);
        SharedSystemClock.withSystemClock(() ->
        SharedUuidIdGen.withUuidIdGen(() ->
        withSessionCounter(bump, () ->
        run {
            run {
                run {
                    run {
                        run {
                            run {
                                run {
                                    f()
                                } with handler SharedLocationCatalog.LocationCatalog {
                                    def all(k) = { bump(); k(Nil) }
                                }
                            } with handler ShipperExistenceChecker.ShipperExistenceChecker {
                                def resolveShipperId(_c, k) = { bump(); k(None) }
                            }
                        } with handler CargoRepo.CargoRepo {
                            def save(_c, k)            = { bump(); k() }
                            def findByBookingId(_i, k) = { bump(); k(None) }
                            def findAll(k)             = { bump(); k(Nil) }
                        }
                    } with handler ShipperRepo.ShipperRepo {
                        def save(_s, k)            = { bump(); k() }
                        def findByEmail(_e, k)     = { bump(); k(None) }
                        def findByShipperId(_i, k) = { bump(); k(None) }
                        def findAll(k)             = { bump(); k(Nil) }
                        def search(_q, k)          = { bump(); k(Nil) }
                    }
                } with handler TrackingReadDb.ReadDb {
                    def findByTrackingNumber(_n, k) = { bump(); k(None) }
                }
            } with handler SharedPassword.Password {
                def hash(_p, k)       = { bump(); k("") }
                def verify(_p, _h, k) = { bump(); k(false) }
            }
        } with handler SharedUserRepo.UserRepo {
            def findByUsername(_n, k) = { bump(); k(None) }
            def recordFailure(_i, k)  = { bump(); k() }
            def resetFailures(_i, k)  = { bump(); k() }
        })))

    /// `Session` のカウンタ（入れ子の深さを分けるため関数へ切り出す）
    def withSessionCounter(bump: Unit -> Unit \ IO,
                           f: Unit -> a \ ef + SharedSession.Session):
            a \ (ef - SharedSession.Session) + IO =
        run {
            f()
        } with handler SharedSession.Session {
            def current(k)           = { bump(); k(None) }
            def login(_p, k)         = { bump(); k("") }
            def logout(k)            = { bump(); k() }
            def csrfToken(k)         = { bump(); k(None) }
            def terminationReason(k) = { bump(); k(None) }
        }

    def sampleRequest(): Request =
        fullRequest(SharedHttp.HttpMethod.Get, "/health/live", Map.empty(),
                    Map.empty(), Map.empty(), Map.empty())
}
