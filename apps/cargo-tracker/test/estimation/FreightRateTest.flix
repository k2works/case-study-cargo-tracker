///
/// 運賃計算の単体テスト（US01・IT8）。
///
/// # このテストが見るもの
///
/// 運賃は**概算**であり、値そのものに唯一の正解は無い。
/// 固定するのは値ではなく、**業務上あり得ない結果が出ないこと**である。
///
/// - 積替が増えるほど高くなる（直行より積替のほうが安いことはない）
/// - 重い貨物のほうが高い
/// - 危険物・冷凍は一般貨物より高い
///
/// 単価そのものを固定するテストも 1 つだけ置く。値を変えたときに
/// **意図せず変わったのか、意図して変えたのか**が分かるようにするためである。
///
mod FreightRateTest {
    use EstimationFreightRate.estimate
    use EstimationModel.CargoKind
    use SharedMoney.senOf

    /// 1.2 トン（Try T4 の「大阪からロサンゼルスへ 1.2 トン」）
    def oneAndAHalfTon(): Int64 = 1200000i64

    ///
    /// **積替が増えるほど高くなること**。
    ///
    /// 積替のたびに荷役と書類が発生する。**直行より積替 2 回のほうが安い**
    /// という結果は業務上あり得ず、出れば営業は見積を信用しない。
    ///
    /// この式が偽になる入力: 基本料金に区間数を掛けない実装。
    ///
    @Test
    def testCostsMoreWithMoreLegs(): Unit \ Assert =
        let direct = estimate(CargoKind.General, oneAndAHalfTon(), 1);
        let oneTransship = estimate(CargoKind.General, oneAndAHalfTon(), 2);
        let twoTransships = estimate(CargoKind.General, oneAndAHalfTon(), 3);
        Assert.assertTrue(senOf(direct) < senOf(oneTransship));
        Assert.assertTrue(senOf(oneTransship) < senOf(twoTransships))

    /// 重い貨物のほうが高いこと
    @Test
    def testCostsMoreForHeavierCargo(): Unit \ Assert =
        Assert.assertTrue(
            senOf(estimate(CargoKind.General, 1000000i64, 1)) <
            senOf(estimate(CargoKind.General, 2000000i64, 1)))

    ///
    /// **危険物・冷凍は一般貨物より高いこと**。
    ///
    /// 危険物は書類・隔離積付・保険が、冷凍は電源とリーファーの確保が要る。
    /// この式が偽になる入力: 割増を掛け忘れた実装（3 種別が同額になる）。
    ///
    @Test
    def testAppliesSurchargeByCargoKind(): Unit \ Assert =
        let general = senOf(estimate(CargoKind.General, oneAndAHalfTon(), 1));
        let refrigerated = senOf(estimate(CargoKind.Refrigerated, oneAndAHalfTon(), 1));
        let hazardous = senOf(estimate(CargoKind.Hazardous, oneAndAHalfTon(), 1));
        Assert.assertTrue(general < refrigerated);
        Assert.assertTrue(refrigerated < hazardous)

    ///
    /// **単価を固定する**（意図しない変更を検出する）。
    ///
    /// 基本料金 50,000 円 × 1 区間 + 120 円/kg × 1,200 kg = 194,000 円。
    /// 一般貨物は割増なし。
    ///
    /// ここが落ちたら、**単価か計算式が変わった**ということである。
    /// 意図した変更なら期待値を直す。意図していないなら実装を戻す。
    ///
    @Test
    def testCalculatesKnownExample(): Unit \ Assert =
        Assert.assertEq(expected = "194,000 円",
                        SharedMoney.toYenText(estimate(CargoKind.General, oneAndAHalfTon(), 1)))

    ///
    /// **1 kg 未満の貨物でも重量分が 0 に潰れないこと**。
    ///
    /// グラム単価を「キログラム単価 / 1000」で先に求めると、
    /// 120 / 1000 = 0（整数除算）になり、**重量が運賃に効かなくなる**。
    /// 500 g と 900 g が同額になっても、基本料金があるため合計は
    /// もっともらしく見える——だから気付けない。
    ///
    /// この式が偽になる入力: 単価を先に割ってから重量を掛ける実装。
    ///
    @Test
    def testKeepsWeightFareForSubKilogramCargo(): Unit \ Assert =
        Assert.assertTrue(
            senOf(estimate(CargoKind.General, 500i64, 1)) <
            senOf(estimate(CargoKind.General, 900i64, 1)))
}
