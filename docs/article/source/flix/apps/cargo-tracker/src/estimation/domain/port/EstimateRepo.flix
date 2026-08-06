///
/// 見積リポジトリ（出力ポート。US01・IT8）。
///
/// 効果として宣言し、実装は `infrastructure/repositories` が注入する
/// （[アーキテクチャ設計](../../../../../../docs/design/architecture_backend.md) の
/// ポートとアダプタ）。ドメイン・アプリケーション層は JDBC を知らない。
///
mod EstimateRepo {
    use EstimationModel.Estimate

    pub eff EstimateRepo {
        ///
        /// 見積とルート候補を保存する。
        ///
        /// **候補も一緒に保存する**。候補は見積の一部（値オブジェクト）であり、
        /// 単独では意味を持たない。別々に保存できる形にすると、
        /// 「見積はあるが候補が入っていない」中途半端な状態が作れる。
        ///
        def save(estimate: Estimate): Unit

        /// 見積番号の元になった識別子で 1 件引く
        def findByEstimateId(estimateId: String): Option[Estimate]

        ///
        /// 一覧する。
        ///
        /// **新しい順**である。見積は「さっき出したもの」を確認する用途が
        /// 中心であり、古い順に並べると毎回末尾まで送る必要がある
        /// （予約・荷主の一覧が識別子順なのは、探す手掛かりが番号だからである。
        /// 見積の手掛かりは**時間**である）。
        ///
        def findAll(): List[Estimate]
    }
}
