///
/// 予約への追跡番号の記録（US14。IT10）。
///
/// **Tracking が呼ぶ**（`composition/acl/` のアダプタ経由）。
/// 呼び出し元が誰であっても、`BookingModel.issueTrackingNumber` を通る限り
/// 壊れた状態は入らない（[ADR-0012](../../../../../../docs/adr/ADR-0012-cross-context-writes-go-through-the-target-aggregate.md)）。
///
mod BookingIssueTrackingNumber {
    use BookingModel.BookingError
    use CargoRepo.CargoRepo

    ///
    /// 結果。**`Cargo` を返さない**——呼び出し元は ACL アダプタであり、
    /// Booking の集約を受け取ると Tracking 側へ漏れる余地ができる。
    ///
    pub enum IssueResult with Eq, ToString {
        case Issued
        case NotFound
        case Rejected(BookingError)
    }

    ///
    /// 追跡番号を記録する。
    ///
    /// **`findForUpdate` で行を押さえてから読む**。二重発行の検査は
    /// 「読む → 比べる → 書く」であり、その 3 つの間に別のトランザクションが
    /// 書けば両方が「未発行」を読んで両方が書ける（IT9 レビュー H2 と同じ形）。
    ///
    /// DB の UNIQUE 制約（`cargo.tracking_number`）が最後の砦だが、
    /// **制約に頼るのは番号が衝突した場合だけ**である。同じ予約への二重発行は
    /// 番号が違うため制約では止まらない——ここで押さえる必要がある。
    ///
    pub def issue(bookingId: String, trackingNumber: String): IssueResult \ CargoRepo =
        match CargoRepo.findForUpdate(bookingId) {
            case None    => IssueResult.NotFound
            case Some(c) =>
                match BookingModel.issueTrackingNumber(c, trackingNumber) {
                    case Err(e)      => IssueResult.Rejected(e)
                    case Ok(updated) => {
                        CargoRepo.saveTrackingNumber(updated);
                        IssueResult.Issued
                    }
                }
        }
}
