package cargotracker.shared.interfaces.web

import cargotracker.auth.domain.model.valueobjects.Role
import cargotracker.booking.domain.model.aggregates.Cargo

/** ロール別ダッシュボードのデータ集計を pure function で表現する（IT3 タスク 0.6）。
  *
  * Controller から純粋な関数として呼び出し、テストはサービス層を経由せずに高速に行う。
  */
object DashboardComposer:

  /** ダッシュボードに表示する内容を表現するビューモデル。 */
  final case class DashboardView(
      username: String,
      roles: Set[Role],
      routeProposed: Seq[Cargo]
  ):
    def canSeeSales: Boolean = roles.contains(Role.Sales) || roles.contains(Role.MasterAdmin)
    def canSeeRouting: Boolean =
      roles.contains(Role.RouteDesigner) || roles.contains(Role.MasterAdmin)
    def canSeeTracking: Boolean =
      roles.contains(Role.Tracker) || roles.contains(Role.MasterAdmin)
    def canSeeSettlement: Boolean =
      roles.contains(Role.Settlement) || roles.contains(Role.MasterAdmin)
    def canSeeMasterAdmin: Boolean = roles.contains(Role.MasterAdmin)

  /** RouteProposed 一覧を取得するか判定する。経路設計者または MasterAdmin のみ。 */
  def shouldFetchRouteProposed(roles: Set[Role]): Boolean =
    roles.contains(Role.RouteDesigner) || roles.contains(Role.MasterAdmin)

  /** Controller から受け取ったデータでビューモデルを組み立てる。 */
  def compose(
      username: String,
      roles: Set[Role],
      fetchRouteProposed: () => Seq[Cargo]
  ): DashboardView =
    val routeProposed =
      if shouldFetchRouteProposed(roles) then fetchRouteProposed() else Seq.empty
    DashboardView(username, roles, routeProposed)
