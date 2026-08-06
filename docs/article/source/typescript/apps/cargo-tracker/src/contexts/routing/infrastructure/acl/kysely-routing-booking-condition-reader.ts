import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type {
  RoutingBookingCondition,
  RoutingBookingConditionReader,
} from '../../application/outboundservices/acl/routing-booking-condition-reader.js';

export class KyselyRoutingBookingConditionReader implements RoutingBookingConditionReader {
  constructor(private readonly db: AppDatabase) {}

  async findRoutingInProgress(bookingId: string): Promise<RoutingBookingCondition | null> {
    const row = await this.db
      .selectFrom('cargo')
      .select([
        'bookingId',
        'originUnlocode as origin',
        'destinationUnlocode as destination',
        'cargoType',
        'arrivalDeadline',
      ])
      .where('bookingId', '=', bookingId)
      .where('bookingStatus', '=', 'ROUTING_IN_PROGRESS')
      .executeTakeFirst();
    return row ? { ...row, arrivalDeadline: new Date(row.arrivalDeadline) } : null;
  }
}
