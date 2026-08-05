/// 違反例: `*Wiring.flix` という名前を避けて `composition` 直下に翻訳を書いた。
///
/// 当初の実装は対象を `*Wiring.flix` に限っていたため、**命名だけで規約を
/// 回避できた**（IT9 レビュー M1）。`acl/` を通らない翻訳が残ると、
/// 「`composition/acl/` のファイル数＝BC 間の翻訳の件数」という
/// ADR-0011 の数え方が成り立たなくなる。
mod Bridges {
    use CargoRepo.CargoRepo
    use VoyageRepo.VoyageRepo

    /// Routing の航海を Booking の旅程へ翻訳している（`acl/` に置くべき処理）
    pub def copyVoyageIntoCargo(bookingId: String): Unit \ CargoRepo + VoyageRepo =
        match CargoRepo.findByBookingId(bookingId) {
            case None    => ()
            case Some(_) => discard VoyageRepo.findAll()
        }
}
