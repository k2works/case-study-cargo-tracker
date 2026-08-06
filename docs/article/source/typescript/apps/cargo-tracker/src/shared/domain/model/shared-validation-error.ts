/**
 * 共有カーネルの不変条件違反（利用者向けに提示可能な検証エラー）。
 * 各 BC の *ValidationError と同じく、インフラ障害等の内部エラーと区別するための型。
 */
export class SharedValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'SharedValidationError';
  }
}
