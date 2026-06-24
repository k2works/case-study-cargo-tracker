package cargotracker.handling.domain.model.ports

/** Handling Context が貨物情報を参照するためのクエリポート (IT8 0.11 / T3)。
  *
  * Booking 集約への直接依存を排除し、ヘキサゴナル境界を維持する。 インフラ層の `BookingCargoForHandlingAdapter` が Booking の `CargoRepository`
  * をラップして実装する。
  *
  * 用途: `HandlingOrchestrator.register` で荷役発生地が経路上の予定地か否かを判定し、`routeDeviation` フラグを自動算出する。
  */
trait HandlingCargoQueryPort:
  /** 指定 bookingId の貨物が、与えられた UN/LOCODE を経路上に持つかを返す。
    *
    *   - true: 経路上の予定地 (routeDeviation = false)
    *   - false: 経路逸脱 (routeDeviation = true)、または貨物が見つからない、または itinerary 未紐付け
    *
    * 「貨物未発見」と「経路逸脱」を呼び出し側で区別したい場合は `findItineraryLocations` を別途使う。
    */
  def isOnRoute(bookingId: String, unLocode: String): Boolean

  /** 指定 bookingId の貨物に紐付く経路上の UN/LOCODE 集合を返す。
    *
    *   - None: 貨物が見つからない or 経路 (itinerary) 未紐付け
    *   - Some(empty): legs 未保持の Itinerary (後方互換、IT5 以前生成データ)
    *   - Some(non-empty): 経路上の予定地
    */
  def findItineraryLocations(bookingId: String): Option[Set[String]]
