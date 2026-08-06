/** Tracking Context のドメイン検証エラー */
export class TrackingValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'TrackingValidationError';
  }
}
