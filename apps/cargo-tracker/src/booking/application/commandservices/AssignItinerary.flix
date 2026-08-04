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
    /// **`findForUpdate` を使わない**。`CargoRepo` に行ロックの操作は無く、
    /// 本 IT では追加しない——同時に同じ予約へ別の経路を割り当てる状況は、
    /// 経路設計者が 2 人で同じ予約を担当したときにだけ起こる。
    /// **後勝ちで壊れるのは「経路が上書きされる」だけ**であり、
    /// 集約の不変条件は保たれる（どちらの旅程も検証を通っている）。
    ///
    /// 楽観的ロックは US25（航海の更新）で 1 件目を実装済みであり、
    /// [ADR-0008](../../../../../../docs/adr/ADR-0008-optimistic-locking-by-content-version.md)
    /// は「3 つを超えたときに共通化する」としている。**2 件目をいま作らない**——
    /// 経路の割り当てで上書きが問題になった時点で入れる。
    ///
    pub def assign(bookingId: String, itinerary: CargoItinerary):
            AssignResult \ CargoRepo =
        match CargoRepo.findByBookingId(bookingId) {
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
        match CargoRepo.findByBookingId(bookingId) {
            case None    => AssignResult.NotFound
            case Some(c) => {
                let detached = BookingModel.detachItinerary(c);
                CargoRepo.saveItinerary(detached);
                AssignResult.Assigned(detached)
            }
        }
}
