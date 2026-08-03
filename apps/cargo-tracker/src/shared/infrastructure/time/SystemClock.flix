///
/// `Clock` 効果のシステム時刻実装。
///
mod SharedSystemClock {
    import java.time.Instant

    use SharedClock.Clock

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
        }
}
