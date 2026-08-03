///
/// `Clock` 効果のシステム時刻実装。
///
mod SharedSystemClock {
    import java.time.Instant
    import java.time.ZoneId

    use SharedClock.Clock

    ///
    /// エポックミリ秒を `YYYY-MM-DD` へ直す。
    ///
    /// 業務のタイムゾーン（システム既定）で日付を決める。UTC で決めると
    /// 日本時間の早朝が「前日」として判定される。
    ///
    /// テスト用の時計からも使えるよう公開する。**日付の決め方を 2 通り
    /// 持たない**ためであり、テストだけ別の規則で日付を作ると、
    /// テストが緑でも本番で日がずれる。
    ///
    pub def isoDateOf(epochMillis: Int64): String \ IO =
        // 呼び出しは 1 つずつ束縛する（連ねるとパースできない）
        let instant = Instant.ofEpochMilli(epochMillis);
        let zoned = instant.atZone(ZoneId.systemDefault());
        let date = zoned.toLocalDate();
        date.toString()

    ///
    /// システム時刻を供給する。
    ///
    /// 合成ルートで 1 度だけ適用する。**トランザクションの外側に置く**。
    /// 内側に置く理由がなく、外側に置けばトランザクションを開かない経路
    /// （`TxMode.NoTx`）でも同じハンドラを使える。
    ///
    pub def withSystemClock(f: Unit -> a \ ef + Clock): a \ (ef - Clock) + IO =
        run {
            f()
        } with handler Clock {
            def nowMillis(k) = k(Instant.now().toEpochMilli())
            // 業務のタイムゾーン（システム既定）で日付を決める。UTC で決めると、
            // 日本時間の早朝に「前日」として判定される
            def today(k) = k(isoDateOf(Instant.now().toEpochMilli()))
        }
}
