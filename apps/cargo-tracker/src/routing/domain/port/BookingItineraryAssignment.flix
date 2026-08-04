///
/// 予約への旅程の割り当て（ACL ポート。US09・US11。IT9）。
///
/// **Routing の語彙だけで定義する**。`Cargo` も `CargoItinerary` も現れない。
/// 現れると Routing のドメイン層が Booking のモデルに依存し、ACL を置いた意味が消える。
///
/// 実装は**合成ルートの `acl/`** が与える
/// （[ADR-0011](../../../../../../docs/adr/ADR-0011-routing-writes-booking-through-its-aggregate.md)）。
/// Booking のアプリケーションサービスを同期的に呼び、
/// **集約が不変条件を検証する**。`cargo` / `leg` テーブルへ直接書く形は採らない。
///
/// # なぜイベントではないのか
///
/// ADR-0009 は当初「Routing がイベントを発行し Booking の購読者が更新する」と
/// 決めていた。IT9 の着手前に**更新される集約が `Cargo` 1 つだけ**であることが
/// 判明し、「1 トランザクション 1 集約」の規約が本操作に適用されないと分かった。
/// 波及先が無いところでイベントを使うと、**手段だけが残る**。
///
mod RoutingBookingItineraryAssignment {

    ///
    /// 割り当てる区間 1 つ分。
    ///
    /// **Routing が持っている情報そのまま**である（航海番号・積込港・荷降港・時刻）。
    /// 翻訳は合成ルートの `acl/` が行う。
    ///
    pub enum AssignedLeg({
        voyageNumber = String,
        loadLocation = String,
        unloadLocation = String,
        loadTime = String,
        unloadTime = String
    })

    pub def assignedLeg(voyageNumber: String, loadLocation: String, unloadLocation: String,
                        loadTime: String, unloadTime: String): AssignedLeg =
        AssignedLeg.AssignedLeg({
            voyageNumber = voyageNumber, loadLocation = loadLocation,
            unloadLocation = unloadLocation, loadTime = loadTime, unloadTime = unloadTime
        })

    pub def legVoyageNumberOf(l: AssignedLeg): String =
        let AssignedLeg.AssignedLeg(x) = l; x#voyageNumber
    pub def legLoadLocationOf(l: AssignedLeg): String =
        let AssignedLeg.AssignedLeg(x) = l; x#loadLocation
    pub def legUnloadLocationOf(l: AssignedLeg): String =
        let AssignedLeg.AssignedLeg(x) = l; x#unloadLocation
    pub def legLoadTimeOf(l: AssignedLeg): String =
        let AssignedLeg.AssignedLeg(x) = l; x#loadTime
    pub def legUnloadTimeOf(l: AssignedLeg): String =
        let AssignedLeg.AssignedLeg(x) = l; x#unloadTime

    ///
    /// 割り当ての結果。
    ///
    /// **理由を区別して返す**。画面が「どう直せばよいか」を示せる必要がある——
    /// 「割り当てできません」だけでは経路設計者は次の一手を選べない。
    ///
    pub enum AssignmentResult with Eq, ToString {
        case Assigned
        /// 予約が見つからない（または経路設計中でない）
        case BookingNotAvailable
        /// 旅程が予約の要件を満たさない（端点の不一致・区間の断絶）
        case ItineraryRejected
    }

    pub eff BookingItineraryAssignment {
        ///
        /// 選ばれた経路を予約へ割り当てる。
        ///
        /// **予約の状態は変えない**。経路が決まっても予約は経路設計中のままであり、
        /// 確定するのは営業が荷主の承認を得たとき（US13）である。
        ///
        def assign(bookingId: String, legs: List[AssignedLeg]): AssignmentResult
    }
}
