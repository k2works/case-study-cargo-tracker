import { Logger } from '@nestjs/common';
import { HandlingActivity } from '../../domain/model/handling-activity.js';
import { HandlingValidationError } from '../../domain/model/handling-validation-error.js';
import {
  HANDLING_ACTIVITY_REGISTERED_EVENT,
  HandlingActivityRegisteredEvent,
} from '../../domain/event/handling-activity-registered-event.js';
import { CARGO_CLAIMED_EVENT, CargoClaimedEvent } from '../../domain/event/cargo-claimed-event.js';
import type { HandlingActivityRepository } from '../../domain/repository/handling-activity-repository.js';
import type { CargoSnapshotAcl } from '../outboundservices/acl/cargo-snapshot-acl.js';
import type { HandlingNotificationPort } from '../outboundservices/acl/handling-notification-port.js';

export class TrackingNumberNotFoundError extends Error {
  constructor(trackingNumber: string) {
    super(`追跡番号が見つかりません: ${trackingNumber}`);
    this.name = 'TrackingNumberNotFoundError';
  }
}

export class CustomsNotClearedError extends Error {
  constructor(bookingId: string) {
    super(`通関申告が CLEARED になるまで引取はできません（予約: ${bookingId}）`);
    this.name = 'CustomsNotClearedError';
  }
}

/** イベント発行ポート（コミット後に emit する。Handling Context ローカル定義） */
export interface EventPublisher {
  emit(event: string, payload: unknown): void;
}

/** 通関状態チェック（HandlingActivityHistory Read Model が提供） */
export interface CustomsClearanceCheck {
  isCustomsCleared(bookingId: string): Promise<boolean>;
}

export interface RegisterHandlingCommand {
  trackingNumber: string;
  type: string;
  location: string;
  completionTime: Date;
  voyageNumber?: string | null;
  operatorName?: string | null;
  consigneeConfirmation?: string | null;
}

export interface RegisterHandlingResult {
  /** 場所不一致だが MISROUTED ではない（RECEIVE/CLAIM の警告） */
  warning: boolean;
  /** LOAD/UNLOAD の場所不一致で経路不適合が確定 */
  misrouted: boolean;
  /** 同一種別・同一日時の登録済み作業があり、登録・イベント・通知をスキップした（冪等） */
  duplicated: boolean;
}

/**
 * 荷役作業登録ユースケース（US15/US16）。
 * 追跡番号で貨物を特定し、CargoSnapshot に対する妥当性検証（デシジョンテーブル）を経て登録する。
 * 登録コミット後に HandlingActivityRegisteredEvent と荷主への状態変更通知を発行する（ADR-009）。
 */
export class RegisterHandlingActivityService {
  private readonly logger = new Logger(RegisterHandlingActivityService.name);

  constructor(
    private readonly activities: HandlingActivityRepository,
    private readonly snapshots: CargoSnapshotAcl,
    private readonly customs: CustomsClearanceCheck,
    private readonly events: EventPublisher,
    private readonly notifier: HandlingNotificationPort,
  ) {}

  async register(command: RegisterHandlingCommand): Promise<RegisterHandlingResult> {
    const snapshot = await this.snapshots.findByTrackingNumber(command.trackingNumber);
    if (snapshot === null) {
      throw new TrackingNumberNotFoundError(command.trackingNumber);
    }
    const activity = HandlingActivity.register({
      bookingId: snapshot.bookingId,
      type: command.type,
      location: command.location,
      completionTime: command.completionTime,
      voyageNumber: command.voyageNumber,
      operatorName: command.operatorName,
    });
    if (activity.type.isClaimType()) {
      if (command.consigneeConfirmation == null || command.consigneeConfirmation.trim().length === 0) {
        throw new HandlingValidationError('引取には荷受人確認（署名または確認コード）が必要です');
      }
      if (!(await this.customs.isCustomsCleared(snapshot.bookingId))) {
        throw new CustomsNotClearedError(snapshot.bookingId);
      }
    }
    const valid = activity.isValidFor(snapshot);
    const misrouted = !valid && activity.type.misroutesOnMismatch();

    // 冪等性: 同一種別・同一日時の作業が登録済みなら再登録せず、イベント・通知も再発行しない
    // （CLAIM の二重 CargoClaimedEvent = 二重精算開始を防ぐ）
    const existing = await this.activities.findByBookingId(snapshot.bookingId);
    const duplicated = existing.some(
      (a) =>
        a.type.equals(activity.type) && a.completionTime.getTime() === activity.completionTime.getTime(),
    );
    if (duplicated) {
      return { warning: !valid && !misrouted, misrouted, duplicated: true };
    }
    await this.activities.save(activity);

    // コミット後副作用（ADR-009）。状態伝播イベント（emit）と宛先通知（notify）は失敗レベルが
    // 異なるため別 try で扱う: emit 失敗は US15 の状態同期が沈黙するため ERROR で可視化する
    try {
      this.events.emit(
        HANDLING_ACTIVITY_REGISTERED_EVENT,
        new HandlingActivityRegisteredEvent(
          snapshot.bookingId,
          command.trackingNumber,
          activity.type.value,
          activity.location.unlocode,
          activity.completionTime,
          activity.voyageNumber?.value ?? null,
          misrouted,
        ),
      );
      if (activity.type.isClaimType()) {
        // 引取済は配送完了 = 精算開始条件（US16 受入基準 4）。Billing の購読は IT7
        this.events.emit(
          CARGO_CLAIMED_EVENT,
          new CargoClaimedEvent(snapshot.bookingId, command.trackingNumber, activity.completionTime),
        );
      }
    } catch (error) {
      this.logger.error(`荷役登録イベントの発行に失敗（状態同期が保留。要リカバリ）: ${String(error)}`);
    }
    try {
      await this.notifier.notifyStatusChange(snapshot.bookingId, activity.type.value);
    } catch (error) {
      this.logger.error(`荷役登録の荷主通知に失敗: ${String(error)}`);
    }
    return { warning: !valid && !misrouted, misrouted, duplicated: false };
  }
}
