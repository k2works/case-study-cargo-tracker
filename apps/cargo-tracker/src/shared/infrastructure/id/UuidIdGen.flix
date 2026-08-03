///
/// `IdGen` 効果の UUID 実装。
///
mod SharedUuidIdGen {
    import java.util.UUID

    use SharedIdGen.IdGen

    ///
    /// UUID を識別子として供給する。
    ///
    /// `randomUUID` は内部で `SecureRandom` を使う。荷主 ID は推測されても
    /// 実害のない値だが、**連番にすると登録件数が外部から推測できる**ため乱数を使う。
    ///
    pub def withUuidIdGen(f: Unit -> a \ ef + IdGen): a \ (ef - IdGen) + IO =
        run {
            f()
        } with handler IdGen {
            def newId(k) = k(UUID.randomUUID().toString())
        }
}
