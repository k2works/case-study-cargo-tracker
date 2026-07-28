/**
 * 予約ドメインの不変条件違反（利用者向けに提示可能な検証エラー）。
 * インフラ障害等の内部エラーと区別するため専用型とする。
 */
export class BookingValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'BookingValidationError';
  }
}
