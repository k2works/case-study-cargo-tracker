mod TestBootstrap {
    use Bootstrap.{findCargo, withInMemoryCargoRepo, probeDatabase}

    /// 効果ハンドラでポートの実装を差し替えられること
    @Test
    def testEffectHandlerInjectsAdapter(): Unit \ Assert =
        Assert.assertEq(expected = "cargo-1", withInMemoryCargoRepo(() -> findCargo("C1")))

    /// ハンドラが返す値によってユースケースの分岐が変わること
    @Test
    def testEffectHandlerNotFound(): Unit \ Assert =
        Assert.assertEq(expected = "not-found", withInMemoryCargoRepo(() -> findCargo("UNKNOWN")))

    /// Java 相互運用で H2（PostgreSQL 互換モード）へ接続できること
    @Test
    def testH2Connection(): Unit \ Assert + IO =
        Assert.assertEq(
            expected = "ok",
            probeDatabase("jdbc:h2:mem:probe;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "org.h2.Driver")
        )
}
