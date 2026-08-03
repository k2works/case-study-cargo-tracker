///
/// 航海リポジトリのポート（出力ポート）。
///
/// [ドメインモデル設計](../../../../../../docs/design/domain-model.md) の
/// Routing Context に対応する。**実装名は `VoyageRepo`** とし、既存のポート
/// （`CargoRepo`・`ShipperRepo`）の命名に揃えている。
///
/// インターフェースではなく効果として宣言する。実装の注入はハンドラで行い、
/// DI コンテナもモックライブラリも要らない（ADR-0002）。
///
mod VoyageRepo {
    use RoutingModel.{Voyage, CargoCapability}

    ///
    /// 航海スケジュールの永続化と検索。
    ///
    pub eff VoyageRepo {
        ///
        /// 航海を登録する。
        ///
        /// **航海番号の重複は呼び出し側が事前に確認する**（`findByVoyageNumber`）。
        /// 一意制約違反を捕まえて分岐する形にすると、利用者に見えるのは 500 になる
        /// （IT4 で荷主のメール重複について同じ判断をした）。
        ///
        def save(voyage: Voyage): Unit

        /// 航海番号で 1 件引く（重複確認と編集画面の呼び出しに使う）
        def findByVoyageNumber(voyageNumber: String): Option[Voyage]

        /// すべての航海を航海番号順に引く
        def findAll(): List[Voyage]

        ///
        /// 既存の航海を**丸ごと**置き換える。
        ///
        /// `save`（登録）と分けるのは、登録と更新を 1 つの操作にまとめると
        /// 「更新したつもりが新規に入る」が静かに起きるためである
        /// （`CargoRepo.updateStatus` と同じ判断）。
        ///
        /// **区間は入れ替える**（削除してから挿入する）。差分更新にすると、
        /// 区間を減らす更新で古い行が残る。
        ///
        def replace(voyage: Voyage): Unit

        ///
        /// 条件で検索する（US07）。
        ///
        /// - `origin` / `destination`: 航程の端点（空なら絞らない）
        /// - `departureFrom` / `departureTo`: 出発日の範囲（`YYYY-MM-DD`。空なら絞らない）
        /// - `capability`: 対応貨物種別（`None` なら絞らない）
        ///
        /// **絞り込みは DB で行う**。全件を取り出してアプリ側で捨てると、
        /// 件数が増えるほど使われない行を運ぶことになる。
        ///
        def search(origin: String, destination: String,
                   departureFrom: String, departureTo: String,
                   capability: Option[CargoCapability]): List[Voyage]
    }
}
