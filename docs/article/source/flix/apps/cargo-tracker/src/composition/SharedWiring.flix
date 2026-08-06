///
/// 共有カーネル（認証・場所マスタ・時刻・ID 採番）の配線（IT8・TS14）。
///
/// 分割の意図と `src/composition/` に置く理由は [ShipperWiring](ShipperWiring.flix) を参照。
///
/// **BC ではない**が、同じ形で分けている。`Session` はセッション Cookie の指示を
/// 呼び出し側へ返す必要があり（`change` コールバック）、**他の BC と違って引数を取る**。
/// この非対称は本ファイルに閉じ込め、合成ルートからは見えないようにしている。
///
mod SharedWiring {
    use SharedBcryptPassword.withBcryptPassword
    use SharedClock.Clock
    use SharedIdGen.IdGen
    use SharedJdbcLocationCatalog.withJdbcLocationCatalog
    use SharedJdbcSession.withJdbcSession
    use SharedJdbcUserRepo.withJdbcUserRepo
    use SharedLocationCatalog.LocationCatalog
    use SharedPassword.Password
    use SharedSession.Session
    use SharedSystemClock.withSystemClock
    use SharedUserRepo.UserRepo
    use SharedUuidIdGen.withUuidIdGen

    ///
    /// DB を要する効果（トランザクションの内側で適用する）。
    ///
    /// `Password` を含めないのは、BCrypt が DB を使わないためである。
    /// `Clock` と `IdGen` も同じ理由で外側に置く。
    ///
    pub type alias DbEff = Session + UserRepo + LocationCatalog

    /// DB を要さない効果（トランザクションの外側で適用してよい）
    pub type alias PureEff = Password + Clock + IdGen

    /// 本 BC が要求する効果の全体
    pub type alias Eff = DbEff + PureEff

    ///
    /// 本番のアダプタを適用する（トランザクションの内側）。
    ///
    /// `cookieId` はリクエストの Cookie、`onChange` はセッションの発行・破棄を
    /// 呼び出し側へ返すコールバックである。**この 2 つが `Session` ハンドラと
    /// 一緒に動かないと、ログインが成立しても Cookie が出ない**
    /// （[IT8 計画](../../../../docs/development/iteration_plan-8.md) のタスク 0.1 で
    /// 「実行時に静かに壊れうる唯一の経路」として挙げた点）。
    ///
    pub def withDb(cookieId: Option[String],
                   onChange: Option[String] -> Unit \ IO,
                   f: Unit -> a \ ef + DbEff): a \ (ef - DbEff) + Clock + SharedDbTx.Tx + IO =
        withJdbcSession(cookieId, onChange, () ->
            withJdbcUserRepo(() ->
                withJdbcLocationCatalog(f)))

    /// DB を使わない経路（`TxMode.NoTx`）で適用する
    pub def withoutDb(f: Unit -> a \ ef + DbEff): a \ (ef - DbEff) =
        run {
            run {
                run {
                    f()
                } with handler LocationCatalog {
                    def all(_k) = WiringGuard.noDbViolation("LocationCatalog")
                }
            } with handler UserRepo {
                def findByUsername(_n, _k) = WiringGuard.noDbViolation("UserRepo")
                def recordFailure(_id, _k) = WiringGuard.noDbViolation("UserRepo")
                def resetFailures(_id, _k) = WiringGuard.noDbViolation("UserRepo")
            }
        } with handler Session {
            def current(_k)           = WiringGuard.noDbViolation("Session")
            def login(_p, _k)         = WiringGuard.noDbViolation("Session")
            def logout(_k)            = WiringGuard.noDbViolation("Session")
            def csrfToken(_k)         = WiringGuard.noDbViolation("Session")
            def terminationReason(_k) = WiringGuard.noDbViolation("Session")
        }

    ///
    /// DB を要さないアダプタを適用する（トランザクションの外側）。
    ///
    /// **`NoTx` の経路でも本物を供給してよい**。DB を使わないためである。
    ///
    pub def withPure(f: Unit -> a \ ef + PureEff): a \ (ef - PureEff) + IO =
        withUuidIdGen(() ->
            withSystemClock(() ->
                withBcryptPassword(f)))

    /// 効果を「使った」ことにする（`lift` のため。常に `false` で呼ばれる）
    pub def touch(shouldRun: Bool): Unit \ Eff =
        if (shouldRun) {
            discard Session.current();
            discard UserRepo.findByUsername("");
            discard LocationCatalog.all();
            discard Password.verify("", "");
            discard Clock.nowMillis();
            discard IdGen.newId()
        } else ()
}
