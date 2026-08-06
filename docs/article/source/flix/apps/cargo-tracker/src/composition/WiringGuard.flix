///
/// 配線の誤りを実行時に落とす仕組み（IT8・TS14）。
///
/// BC 別の配線ファイル（`*Wiring.flix`）が共通で使う。
/// `AppComposition` に置くと、各 Wiring から合成ルート本体への参照が生まれ、
/// **分割した意味が薄れる**ため独立させた。
///
mod WiringGuard {

    ///
    /// `TxMode.NoTx` と宣言したルートが DB を要する効果を使ったときに落とす。
    ///
    /// **既定値を返さない**。既定値で通すと、`NoTx` と宣言したルートが実際には
    /// DB を要求していたときに**未認証として静かに通ってしまう**。
    /// ここで落ちるのは実装の誤りであり、開発とテストの段階で必ず表面化する。
    ///
    /// `NoTx` のルートが `Anonymous` に限られることは
    /// `AppRoutesTest.testNoTxRoutesAreAnonymous` が固定している。
    ///
    pub def noDbViolation(effectName: String): a =
        bug!("TxMode.NoTx と宣言したルートが ${effectName} を要求しました。" +
             "ルート定義の TxMode を見直してください")
}
