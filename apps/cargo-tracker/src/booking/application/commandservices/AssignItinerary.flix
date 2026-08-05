///
/// 旅程の割り当て（US09・US11。IT9）。
///
/// **Routing から呼ばれる Booking のユースケース**である
/// （[ADR-0011](../../../../../../docs/adr/ADR-0011-routing-writes-booking-through-its-aggregate.md)）。
/// 呼び出し元は合成ルートの ACL アダプタであり、Routing のドメイン層ではない。
///
/// ここでの責務は 3 つに限る。
///
/// 1. 予約を引く（行を押さえる）
/// 2. 集約に割り当てを依頼する（**判断は集約が持つ**）
/// 3. 保存する
///
mod BookingAssignItinerary {
    use BookingModel.{Cargo, CargoItinerary, BookingError}
    use CargoRepo.CargoRepo

    /// 割り当ての結果
    pub enum AssignResult {
        case Assigned(Cargo)
        /// 予約が見つからない
        case NotFound
        /// 集約が拒んだ（状態・端点・連結）
        case Rejected(BookingError)
    }

    ///
    /// 旅程を割り当てる。
    ///
    /// **`findForUpdate` で行を押さえてから読む**（IT9 レビュー H2）。
    ///
    /// 当初は「後勝ちで壊れるのは経路が上書きされるだけ」と判断して
    /// 素の読み取りにしていたが、**それは US13（予約の確定）が無かった前提**である。
    /// 同じ IT で確定操作が入ったため、営業の `confirm` と経路設計者の `assign` が
    /// 交錯すると「荷主が承認していない経路での確定」が成立するようになった。
    /// 集約の検査は*読んだ瞬間の状態*にしか効かず、DB の行には効かない。
    ///
    /// [ADR-0008](../../../../../../docs/adr/ADR-0008-optimistic-locking-by-content-version.md)
    /// の「3 つを超えたら共通化する」は**共通化の判断条件であって適用可否の
    /// 条件ではない**。同 ADR が採る「読む時点で行を押さえる」をここでも使う。
    ///
    pub def assign(bookingId: String, itinerary: CargoItinerary):
            AssignResult \ CargoRepo =
        match CargoRepo.findForUpdate(bookingId) {
            case None    => AssignResult.NotFound
            case Some(c) =>
                match BookingModel.attachItinerary(c, itinerary) {
                    case Err(e)       => AssignResult.Rejected(e)
                    case Ok(assigned) => {
                        CargoRepo.saveItinerary(assigned);
                        AssignResult.Assigned(assigned)
                    }
                }
        }

    ///
    /// 旅程を外す（経路の選び直し・US13 の差し戻し）。
    ///
    /// **集約は拒まない**（外すのはいつでもできる）。予約が無い場合だけ失敗する。
    ///
    pub def detach(bookingId: String): AssignResult \ CargoRepo =
        match CargoRepo.findForUpdate(bookingId) {
            case None    => AssignResult.NotFound
            case Some(c) => {
                let detached = BookingModel.detachItinerary(c);
                CargoRepo.saveItinerary(detached);
                AssignResult.Assigned(detached)
            }
        }
}
