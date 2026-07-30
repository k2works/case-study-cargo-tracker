import { Location } from '../../../../shared/domain/model/location.js';
import { HandlingValidationError } from './handling-validation-error.js';
import { HandlingType } from './handling-type.js';
import { HandlingVoyageNumber } from './handling-voyage-number.js';
import type { CargoSnapshot } from './cargo-snapshot.js';

export interface RegisterHandlingActivityParams {
  bookingId: string;
  type: string;
  location: string;
  completionTime: Date;
  voyageNumber?: string | null;
  operatorName?: string | null;
  consigneeConfirmation?: string | null;
}

/**
 * 荷役作業（集約ルート）。荷役作業の登録と妥当性検証を担う。
 * 妥当性は CargoSnapshot（ACL 経由の貨物情報）に対するデシジョンテーブルで判定する:
 *
 * | 種別    | Voyage 必須 | 場所チェック                     | 不一致時       |
 * | RECEIVE | 不要        | 出発港（origin）と一致           | 警告           |
 * | LOAD    | 必須        | Leg.loadLocation・航海と一致     | MISROUTED      |
 * | UNLOAD  | 必須        | Leg.unloadLocation・航海と一致   | MISROUTED      |
 * | CLAIM   | 不要        | 目的港（destination）と一致      | 警告           |
 * | CUSTOMS | 不要        | なし                             | -              |
 */
export class HandlingActivity {
  private constructor(
    readonly id: number | null,
    readonly bookingId: string,
    readonly type: HandlingType,
    readonly location: Location,
    readonly completionTime: Date,
    readonly voyageNumber: HandlingVoyageNumber | null,
    readonly operatorName: string | null,
    readonly consigneeConfirmation: string | null,
  ) {}

  static register(params: RegisterHandlingActivityParams): HandlingActivity {
    if (params.bookingId.trim().length === 0) {
      throw new HandlingValidationError('予約 ID は必須です');
    }
    if (Number.isNaN(params.completionTime.getTime())) {
      throw new HandlingValidationError('作業日時が不正です');
    }
    const type = HandlingType.of(params.type);
    const voyageNumber =
      params.voyageNumber != null && params.voyageNumber.trim().length > 0
        ? HandlingVoyageNumber.of(params.voyageNumber)
        : null;
    if (type.requiresVoyageNumber() && voyageNumber === null) {
      throw new HandlingValidationError(`${type.value} には航海番号が必要です`);
    }
    // 荷受人確認は引き渡し証明のため CLAIM（引取）でのみ保持する。
    const consigneeConfirmation = type.isClaimType()
      ? (params.consigneeConfirmation ?? null)
      : null;
    return new HandlingActivity(
      null,
      params.bookingId,
      type,
      Location.of(params.location),
      params.completionTime,
      voyageNumber,
      params.operatorName ?? null,
      consigneeConfirmation,
    );
  }

  static reconstruct(params: RegisterHandlingActivityParams & { id: number }): HandlingActivity {
    const activity = HandlingActivity.register(params);
    return new HandlingActivity(
      params.id,
      activity.bookingId,
      activity.type,
      activity.location,
      activity.completionTime,
      activity.voyageNumber,
      activity.operatorName,
      activity.consigneeConfirmation,
    );
  }

  /** 荷役妥当性検証。false のとき、type.misroutesOnMismatch() なら MISROUTED、そうでなければ警告 */
  isValidFor(snapshot: CargoSnapshot): boolean {
    switch (this.type.value) {
      case 'RECEIVE':
        return this.location.unlocode === snapshot.origin;
      case 'LOAD':
        return snapshot.itineraryLegs.some(
          (leg) =>
            leg.loadLocation === this.location.unlocode && leg.voyageNumber === this.voyageNumber?.value,
        );
      case 'UNLOAD':
        return snapshot.itineraryLegs.some(
          (leg) =>
            leg.unloadLocation === this.location.unlocode && leg.voyageNumber === this.voyageNumber?.value,
        );
      case 'CLAIM':
        return this.location.unlocode === snapshot.destination;
      case 'CUSTOMS':
        return true;
    }
  }
}
