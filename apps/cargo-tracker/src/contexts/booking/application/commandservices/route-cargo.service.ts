import { CargoItinerary, Leg } from '../../domain/model/cargo-itinerary.js';
import type { CargoRepository } from '../../domain/repository/cargo-repository.js';
import { CARGO_ROUTED_EVENT, CargoRoutedEvent } from '../../domain/event/cargo-routed-event.js';
import { BookingNotFoundError } from './assign-to-routing.service.js';
import type { EventPublisher } from './book-cargo.service.js';

export interface LegDraft {
  voyageNumber: string;
  loadLocation: string;
  unloadLocation: string;
  loadTime: Date;
  unloadTime: Date;
}

export interface RouteCargoCommand {
  bookingId: string;
  legs: LegDraft[];
}

/**
 * 経路確定・予約紐付けユースケース（US09/US11）。
 * 経路設計者が選択した経路（Leg 群）を CargoItinerary に組み立てて予約へ紐付け、
 * ROUTING_IN_PROGRESS → ROUTE_PROPOSED に遷移させる。
 * Routing Context の RouteCandidate には直接依存せず、Leg のドラフト DTO を境界とする（BC 独立性）。
 */
export class RouteCargoService {
  constructor(
    private readonly cargos: CargoRepository,
    private readonly events: EventPublisher,
  ) {}

  async route(command: RouteCargoCommand): Promise<void> {
    const cargo = await this.cargos.findByBookingId(command.bookingId);
    if (cargo === null) {
      throw new BookingNotFoundError(command.bookingId);
    }
    const itinerary = CargoItinerary.of(command.legs.map((leg) => Leg.of(leg)));
    cargo.assignRoute(itinerary);
    await this.cargos.update(cargo);
    this.events.emit(
      CARGO_ROUTED_EVENT,
      new CargoRoutedEvent(
        command.bookingId,
        command.legs.map((leg) => leg.voyageNumber),
      ),
    );
  }
}
