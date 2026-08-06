///
/// 貨物の写しの取得（ACL ポート。US15・IT10）。
///
/// **Handling の語彙だけで定義する**。`Cargo` も `CargoItinerary` も現れない。
/// 現れると Handling のドメイン層が Booking のモデルに依存し、ACL を置いた意味が消える。
///
/// # 名前について
///
/// `CargoSnapshot` は**値オブジェクト**である（設計ドキュメントのとおり）。
/// 取得口に同じ名前を付けると同名別概念になるため、本ポートは
/// `CargoSnapshotSource` と名付けた。
///
/// 実装は**合成ルートの `acl/`** が与える（`arch-lint` 規約 11・ADR-0012）。
///
mod HandlingCargoSnapshotSource {
    use HandlingModel.CargoSnapshot

    pub eff CargoSnapshotSource {
        ///
        /// 追跡番号で貨物の写しを引く。
        ///
        /// **追跡番号で引く**（US15 受入基準 1）。荷役作業員が現場で手にしているのは
        /// 貨物に貼られた追跡番号であり、予約番号ではない。
        ///
        /// 見つからない場合は `None`。**エラーではなく「未検出」として扱う**——
        /// 番号の打ち間違いは日常的に起きる。
        ///
        def findByTrackingNumber(trackingNumber: String): Option[CargoSnapshot]
    }
}
