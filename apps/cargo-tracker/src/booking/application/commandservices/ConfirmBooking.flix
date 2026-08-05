///
/// 予約の確定・差し戻し・キャンセル（US13。IT9）。
///
/// **営業担当者の職掌**である。荷主が承認したことを記録し、
/// 変更希望なら経路設計へ戻し、見送りならキャンセルする。
///
/// ここでの責務は「予約を引く → 集約に依頼する → 保存する」に限る。
/// **判断は集約が持つ**（確定できる状態か・経路があるか）。
///
mod BookingConfirmBooking {
    use BookingModel.{Cargo, BookingError}
    use CargoRepo.CargoRepo

    /// 操作の結果
    pub enum ConfirmResult {
        case Done(Cargo)
        case NotFound
        case Rejected(BookingError)
    }

    ///
    /// 予約を確定する（荷主が承認した）。
    ///
    /// **旅程まで読む**。集約が「経路が割り当てられているか」を見るため、
    /// 旅程を読まずに引くと**常に経路なしと判定される**。
    ///
    pub def confirm(bookingId: String): ConfirmResult \ CargoRepo =
        applyTo(bookingId, BookingModel.confirm)

    /// 経路設計へ差し戻す（荷主がルート変更を希望した）
    pub def sendBackToRouting(bookingId: String): ConfirmResult \ CargoRepo =
        applyTo(bookingId, BookingModel.sendBackToRouting)

    /// 予約をキャンセルする（荷主が見送った）
    pub def cancel(bookingId: String): ConfirmResult \ CargoRepo =
        applyTo(bookingId, BookingModel.cancel)

    ///
    /// 集約の操作を適用して保存する。
    ///
    /// **3 つの操作で同じ手順を書き写さない**。引く・依頼する・保存するは
    /// どれも同じであり、違うのは集約に何を頼むかだけである。
    ///
    /// **状態と旅程の両方を保存する**。差し戻しは `BookingStatus` と
    /// `RoutingStatus` の両方を変えるため、片方だけ保存すると
    /// 「経路設計中なのに経路提案済」という読めない行が残る。
    ///
    /// **`findForUpdate` で行を押さえてから読む**（IT9 レビュー H2）。
    /// 経路設計者の割り当てと交錯すると、確定が古い旅程を書き戻して
    /// 「荷主が承認していない経路での確定」になる。
    ///
    def applyTo(bookingId: String,
                operation: Cargo -> Result[BookingError, Cargo]): ConfirmResult \ CargoRepo =
        match CargoRepo.findForUpdate(bookingId) {
            case None    => ConfirmResult.NotFound
            case Some(c) =>
                match operation(c) {
                    case Err(e)      => ConfirmResult.Rejected(e)
                    case Ok(updated) => {
                        CargoRepo.updateStatus(updated);
                        CargoRepo.saveItinerary(updated);
                        ConfirmResult.Done(updated)
                    }
                }
        }
}
