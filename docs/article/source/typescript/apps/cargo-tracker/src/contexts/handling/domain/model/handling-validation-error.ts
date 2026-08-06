/** Handling Context のドメイン検証エラー */
export class HandlingValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'HandlingValidationError';
  }
}
