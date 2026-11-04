package cargotracker.sharedkernel

/** 利用者のロール（アクターと 1 対 1。US26 の AI の仮定）。 */
enum Role:
  case Shipper, Sales, RouteDesigner, TrackingManager, HandlingWorker, Accountant

/** 認証済みの利用者。他の Unit が依存するのはこれだけ（認証方式の詳細は漏らさない。ADR-001・BE-09）。 */
final case class Principal(userId: Ulid, role: Role)
