import type { Cargo } from '../model/cargo.js';

/** 貨物リポジトリ出力ポート */
export interface CargoRepository {
  save(cargo: Cargo): Promise<number>;
  findByBookingId(bookingId: string): Promise<Cargo | null>;
  update(cargo: Cargo): Promise<void>;
  /** 経路状態（NOT_ROUTED / ROUTED / MISROUTED）を更新する。荷役イベント購読（US15）から使用 */
  updateRoutingStatus(bookingId: string, routingStatus: string): Promise<void>;
}
