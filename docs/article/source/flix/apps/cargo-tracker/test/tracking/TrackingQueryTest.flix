mod TrackingQueryTest {
    use InMemoryHandlers.withInMemoryReadDb
    use TrackingQuery.findByTrackingNumber
    use TrackingFixtures.{detailWithArrival, sampleTrackingNumber, loadEventTime, receiveEventTime}

    def sampleData(): List[TrackingView.TrackingDetailView] =
        detailWithArrival() :: Nil

    ///
    /// 効果ハンドラを差し替えるだけでクエリサービスをテストできること。
    ///
    /// これは本アーキテクチャの中核的な仮定であり、DB を起動せずに
    /// アプリケーション層を検証できることを示す。
    ///
    @Test
    def testFindByTrackingNumber(): Unit \ Assert =
        let result = withInMemoryReadDb(sampleData(), () -> findByTrackingNumber(sampleTrackingNumber()));
        match result {
            case None    => Assert.fail("追跡情報が見つからなかった")
            case Some(v) => Assert.assertEq(expected = "ONBOARD_CARRIER", TrackingView.transportStatus(v))
        }

    /// 存在しない追跡番号では None を返すこと（エラーではなく正常系）
    @Test
    def testFindByTrackingNumberReturnsNoneWhenAbsent(): Unit \ Assert =
        let result = withInMemoryReadDb(sampleData(), () -> findByTrackingNumber("TRK-99999999-9999"));
        Assert.assertTrue(Option.isEmpty(result))

    /// イベントは新しい順に並ぶこと（TR-2）
    @Test
    def testEventsAreOrderedByTime(): Unit \ Assert =
        let result = withInMemoryReadDb(sampleData(), () -> findByTrackingNumber(sampleTrackingNumber()));
        match result {
            case None    => Assert.fail("追跡情報が見つからなかった")
            case Some(v) =>
                let times = TrackingView.events(v) |> List.map(TrackingView.eventTime);
                Assert.assertEq(expected = loadEventTime() :: receiveEventTime() :: Nil, times)
        }
}
