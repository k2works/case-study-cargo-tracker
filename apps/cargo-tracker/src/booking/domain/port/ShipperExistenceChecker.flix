///
/// 荷主の参照解決（ACL ポート）。
///
/// [ドメインモデル設計](../../../../../../docs/design/domain-model.md) の
/// ビジネスルール 9: **Booking Context は Shipper Context に直接依存せず**、
/// 本ポートを通じて荷主を確認する。
///
/// **`exists(shipperId)` ではなく `resolveShipperId(code)` にした理由**（IT4 で是正）:
/// 利用者が目にする荷主の識別子は**荷主コード**（`SHP-XXXXXXXX`）であり、
/// 内部識別子（UUID）ではない。予約フォームが UUID を求める形にすると、
/// 荷主一覧に UUID が出ていない限り**利用者は予約を完了できない**。
/// 受入テストを書く過程でこの行き止まりに気付いた。
///
/// 解決は存在確認を兼ねる。`None` が返れば、その荷主は存在しない。
///
/// **返すのは識別子だけ**である。氏名や割引率まで返すと Booking Context が
/// Shipper のモデルに依存し始める。予約に必要なのは「どの荷主か」だけである。
///
mod ShipperExistenceChecker {

    pub eff ShipperExistenceChecker {
        /// 荷主コードから荷主 ID を解決する（存在しなければ `None`）
        def resolveShipperId(shipperCode: String): Option[String]

        ///
        /// 荷主 ID から**表示に必要な情報**（荷主コード・氏名 / 社名）を引く。
        ///
        /// 予約詳細は「誰の貨物か」を示す必要がある（US06 受入基準 1）。
        /// **`Shipper` 集約そのものを返さない**。返すと Booking が Shipper の
        /// モデルに依存し、ACL を置いた意味が消える（arch-lint 規約 4）。
        /// 必要なのは表示に足る 2 つの文字列だけである。
        ///
        def describeShipper(shipperId: String): Option[(String, String)]

        ///
        /// 荷主名（部分一致）に該当する荷主 ID を引く。
        ///
        /// 予約一覧を**荷主名で絞る**ために使う（IT6 レビュー M4）。
        /// 荷主から電話で問い合わせが来たとき、営業は荷主コードを知らない。
        ///
        /// **`JdbcCargoRepo` が `shipper` 表を JOIN しない**ためのポートである。
        /// SQL の中で BC をまたぐと `arch-lint` は検出できない（規約 4 の
        /// 既知の穴。[arch_lint_rules.md](../../../../../../docs/design/arch_lint_rules.md)）。
        /// 名前は Shipper Context のもの、予約の絞り込みは Booking のもの——
        /// **境界で「荷主 ID の集合」に翻訳する**。
        ///
        /// 該当が無ければ空リスト。呼び出し側はこれを
        /// 「絞らない」ではなく「**0 件**」として扱う。
        ///
        def findShipperIdsByName(namePart: String): List[String]
    }
}
