///
/// Shipper Context の配線（IT8・TS14）。
///
/// # なぜ BC ごとにファイルを分けるか
///
/// IT7 のレビュー（architect）で、合成ルートが次の状態になっていると指摘された。
///
/// > `AppEff` が 13 効果、`runWithoutDb` の入れ子が 7 段になりました。
/// > BC ポートを 1 本足すたびに (1) `AppEff` の型別名、(2) `touchAll`、
/// > (3) `runWithoutDb` の `noDbViolation` ハンドラ、(4) `runWithDb` の
/// > `withJdbc*` の 4 箇所を同時に直す必要があり、しかも 3 番目は入れ子が深い。
/// > 残りの 4 BC が入ると 11 段になります。
///
/// **触る箇所の数（4）は減らない**——効果は静的な型であり、ハンドラのリストを
/// 畳み込むことはできないためである。**減るのは 1 箇所あたりの分量**である。
///
/// | | IT7 まで | IT8 以降 |
/// | :--- | :--- | :--- |
/// | `AppEff` | 13 効果を 1 行に列挙 | BC 別名を 5 つ足す |
/// | `touchAll` | 13 行の `discard` | BC ごとに 1 行 |
/// | `runWithoutDb` | BC ごとに 5-7 行の `bug!` ハンドラ | BC ごとに 1 行 |
/// | `runInTransaction` | BC ごとに 1 行 | BC ごとに 1 行 |
///
/// **ハンドラの詳細は BC の配線ファイルへ移る**。合成ルートは
/// 「どの BC をどの順で配線するか」だけを述べる。
///
/// # なぜ `src/composition/` に置くか
///
/// 効果ハンドラの**合成**（2 段以上の入れ子）は合成ルートとテストにのみ現れる
/// （`arch-lint` 規約 5）。BC の `infrastructure/` に置くと規約違反になる。
/// **規約を曲げずに分割する**には、合成ルートのディレクトリ内で分けるしかない。
///
mod ShipperWiring {
    use ShipperJdbcRepo.withJdbcShipperRepo
    use ShipperRepo.ShipperRepo

    /// 本 BC が要求する効果
    pub type alias Eff = ShipperRepo

    /// 本番のアダプタを適用する
    pub def withDb(f: Unit -> a \ ef + Eff): a \ (ef - Eff) + SharedDbTx.Tx + IO =
        withJdbcShipperRepo(f)

    ///
    /// DB を使わない経路（`TxMode.NoTx`）で適用する。
    ///
    /// **既定値を返さず落とす**。`NoTx` と宣言したルートが実際には DB を要求して
    /// いたとき、既定値で通すと**未認証として静かに通ってしまう**。
    ///
    pub def withoutDb(f: Unit -> a \ ef + Eff): a \ (ef - Eff) =
        run {
            f()
        } with handler ShipperRepo {
            def save(_s, _k)            = WiringGuard.noDbViolation("ShipperRepo")
            def findByEmail(_e, _k)     = WiringGuard.noDbViolation("ShipperRepo")
            def findByShipperId(_i, _k) = WiringGuard.noDbViolation("ShipperRepo")
            def findAll(_k)             = WiringGuard.noDbViolation("ShipperRepo")
            def search(_q, _k)          = WiringGuard.noDbViolation("ShipperRepo")
        }

    ///
    /// 効果を「使った」ことにする（`lift` のため）。
    ///
    /// 引数は常に `false` で呼ばれ、**中の操作は 1 度も実行されない**。
    /// 詳細は `AppComposition.touchAll` を参照。
    ///
    pub def touch(shouldRun: Bool): Unit \ Eff =
        if (shouldRun) discard ShipperRepo.findAll() else ()
}
