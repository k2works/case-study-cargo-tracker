///
/// 荷主リポジトリのポート（出力ポート）。
///
/// [ドメインモデル設計](../../../../../../docs/design/domain-model.md) の
/// `ShipperRepository` に対応する。**実装名は `ShipperRepo`** とし、既存のポート
/// （`UserRepo`・`ReadDb`）の命名に揃えている。
///
/// インターフェースではなく効果として宣言する。実装の注入はハンドラで行い、
/// DI コンテナもモックライブラリも要らない（[ADR-0002](../../../../../../docs/adr/ADR-0002-self-built-web-and-security.md)）。
///
mod ShipperRepo {
    use ShipperModel.Shipper

    ///
    /// 荷主の永続化と検索。
    ///
    /// **`findByEmail` を持つ理由**: メールの一意はビジネスルール 2 であり、
    /// 重複時に「既存の荷主を提示する」という業務上の振る舞い（US02 受入基準 2）が要る。
    /// 一意制約違反を捕まえて分岐する形にすると、**既存の荷主を画面へ出せない**。
    ///
    pub eff ShipperRepo {
        /// 荷主を保存する
        def save(shipper: Shipper): Unit

        /// メールアドレスで荷主を引く（重複判定と既存荷主の提示に使う）
        def findByEmail(email: String): Option[Shipper]

        /// 荷主 ID で荷主を引く
        def findByShipperId(shipperId: String): Option[Shipper]

        /// 荷主を一覧する
        def findAll(): List[Shipper]
    }
}
