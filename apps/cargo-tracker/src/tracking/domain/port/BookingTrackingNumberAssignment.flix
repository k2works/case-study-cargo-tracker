///
/// 予約への追跡番号の記録（ACL ポート。US14。IT10）。
///
/// **Tracking の語彙だけで定義する**。`Cargo` も `BookingStatus` も現れない。
/// 現れると Tracking のドメイン層が Booking のモデルに依存し、ACL を置いた意味が消える。
///
/// 実装は**合成ルートの `acl/`** が与える
/// （[ADR-0012](../../../../../../docs/adr/ADR-0012-cross-context-writes-go-through-the-target-aggregate.md)）。
/// Booking のアプリケーションサービスを同期的に呼び、**集約が不変条件を検証する**。
/// `cargo` テーブルへ直接書く形は採らない。
///
/// # 方向について
///
/// 追跡番号を発行するのは Tracking である（番号は Tracking の識別子であり、
/// `tracking_activity` が採番の台帳になる）。Booking はそれを**記録するだけ**なので、
/// 必要とする側（Tracking）が自分の語彙でポートを宣言する。
///
mod TrackingBookingTrackingNumberAssignment {

    ///
    /// 記録の結果。
    ///
    /// **理由を区別して返す**。画面が「どう直せばよいか」を示せる必要がある。
    ///
    pub enum AssignmentResult with Eq, ToString {
        case Assigned
        /// 予約が見つからない
        case BookingNotFound
        /// 予約が確定していない（追跡番号は確定後にだけ発行できる）
        case BookingNotConfirmed
        /// 既に発行済み
        case AlreadyIssued
    }

    pub eff BookingTrackingNumberAssignment {
        ///
        /// 追跡番号を予約へ記録する。
        ///
        /// 成功すると予約の状態は「追跡番号発行済」へ進む。
        /// **状態を進めるかどうかは Booking の集約が決める**——
        /// ここで `TRACKING_ISSUED` を指示しない。
        ///
        def assign(bookingId: String, trackingNumber: String): AssignmentResult
    }
}
