import type { Cargo } from '../model/cargo.js';

/** 貨物リポジトリ出力ポート */
export interface CargoRepository {
  save(cargo: Cargo): Promise<number>;
  findByBookingId(bookingId: string): Promise<Cargo | null>;
  update(cargo: Cargo): Promise<void>;
}
