export class RoutingValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'RoutingValidationError';
  }
}
