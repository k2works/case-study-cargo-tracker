export interface RoutingBookingCondition {
  bookingId: string;
  origin: string;
  destination: string;
  cargoType: string;
  arrivalDeadline: Date;
}

export interface RoutingBookingConditionReader {
  findRoutingInProgress(bookingId: string): Promise<RoutingBookingCondition | null>;
}
