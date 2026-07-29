import { Location } from '../../../../shared/domain/model/location.js';
import { BookingValidationError } from './booking-validation-error.js';

interface LegProps {
  voyageNumber: string;
  loadLocation: string;
  unloadLocation: string;
  loadTime: Date;
  unloadTime: Date;
}

/**
 * 輸送区間（Booking Context 値オブジェクト）。単一航海での積込港から荷降港までの区間。
 * VoyageNumber は Routing Context 固有型のため、Booking では文字列として保持する（BC 独立性）。
 */
export class Leg {
  private constructor(
    readonly voyageNumber: string,
    readonly loadLocation: Location,
    readonly unloadLocation: Location,
    readonly loadTime: Date,
    readonly unloadTime: Date,
  ) {}

  static of(props: LegProps): Leg {
    const voyageNumber = props.voyageNumber.trim();
    if (voyageNumber.length === 0) {
      throw new BookingValidationError('航海番号は必須です');
    }
    let load: Location;
    let unload: Location;
    try {
      load = Location.of(props.loadLocation);
      unload = Location.of(props.unloadLocation);
    } catch (error) {
      throw new BookingValidationError(
        error instanceof Error ? error.message : '積込地・荷降地の指定が不正です',
      );
    }
    if (load.sameAs(unload)) {
      throw new BookingValidationError('積込地と荷降地は異なる必要があります');
    }
    if (props.loadTime.getTime() > props.unloadTime.getTime()) {
      throw new BookingValidationError('積込時刻は荷降時刻以前である必要があります');
    }
    return new Leg(voyageNumber, load, unload, props.loadTime, props.unloadTime);
  }
}

/** 旅程（Booking Context 値オブジェクト）。1 つ以上の Leg で構成し、連結制約を満たす */
export class CargoItinerary {
  private constructor(readonly legs: Leg[]) {}

  static of(legs: Leg[]): CargoItinerary {
    if (legs.length === 0) {
      throw new BookingValidationError('旅程は 1 つ以上の輸送区間で構成される必要があります');
    }
    for (let i = 0; i < legs.length - 1; i += 1) {
      if (!legs[i].unloadLocation.sameAs(legs[i + 1].loadLocation)) {
        throw new BookingValidationError(
          `輸送区間が連結していません（${legs[i].unloadLocation.unlocode} ≠ ${legs[i + 1].loadLocation.unlocode}）`,
        );
      }
    }
    return new CargoItinerary([...legs]);
  }

  expectedArrivalTime(): Date {
    return this.legs[this.legs.length - 1].unloadTime;
  }
}
