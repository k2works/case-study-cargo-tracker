///
/// 予約情報を経路設計者に引き渡す（US06 / `AssignToRoutingCommand`）。
///
/// 判断はドメイン（`BookingModel.assignToRouting`）にある。ここでの責務は
/// 「引く・呼ぶ・保存する」の順序と、失敗の伝え方だけである。
///
mod BookingAssignToRouting {
    use BookingModel.{Cargo, BookingError}
    use CargoRepo.CargoRepo

    /// 引き渡しの結果
    pub enum AssignResult {
        case Assigned(Cargo)
        /// 指定された予約番号の貨物がない
        case NotFound
        case Invalid(BookingError)
    }

    /// 結果の種別名（テストの失敗メッセージで何が返ったかを示すため）
    pub def resultLabel(r: AssignResult): String = match r {
        case AssignResult.Assigned(_) => "Assigned"
        case AssignResult.NotFound    => "NotFound"
        case AssignResult.Invalid(e)  => "Invalid(${e})"
    }

    ///
    /// 予約を経路設計へ引き渡す。
    ///
    /// **保存するのは遷移に成功したときだけ**である。失敗した場合に保存すると、
    /// 拒否したはずの状態が永続化される。
    ///
    /// 通知の送信は本イテレーションの範囲外（通知基盤が未着手）。状態遷移が
    /// 起きた事実は `bookingStatus` として永続化されており、通知はそこから
    /// 後追いで組み立てられる。**イベントを別に持たない**のは、
    /// 履歴から状態を再導出する形にすると、発生前の状態を失うためである。
    ///
    pub def assign(bookingId: String): AssignResult \ CargoRepo =
        match CargoRepo.findByBookingId(bookingId) {
            case None => AssignResult.NotFound
            case Some(cargo) => match BookingModel.assignToRouting(cargo) {
                case Err(e) => AssignResult.Invalid(e)
                case Ok(assigned) => {
                    CargoRepo.update(assigned);
                    AssignResult.Assigned(assigned)
                }
            }
        }
}
