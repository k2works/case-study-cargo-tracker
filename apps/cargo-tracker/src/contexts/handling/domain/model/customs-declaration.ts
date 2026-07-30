import { HandlingValidationError } from './handling-validation-error.js';
import { CustomsStatus } from './customs-status.js';

export interface RegisterCustomsDeclarationParams {
  declarationNumber: string;
  handlingActivityId: number;
  declaredAt: Date;
  remarks?: string | null;
}

export interface CustomsDeclarationProps {
  declarationNumber: string;
  handlingActivityId: number;
  status: CustomsStatus;
  declaredAt: Date;
  clearedAt: Date | null;
  remarks: string | null;
}

/** 各状態から許可される遷移先。CLEARED / REJECTED は終端（遷移先なし） */
const ALLOWED_TRANSITIONS: Record<CustomsStatus, readonly CustomsStatus[]> = {
  PENDING: [CustomsStatus.CLEARED, CustomsStatus.HELD, CustomsStatus.REJECTED],
  HELD: [CustomsStatus.CLEARED, CustomsStatus.REJECTED],
  CLEARED: [],
  REJECTED: [],
};

/**
 * 通関申告（集約ルート）。通関状態の遷移規則と不変条件を保持する（ADR-010）。
 * 遷移規則:
 * - PENDING からのみ CLEARED / HELD / REJECTED へ
 * - HELD からは CLEARED / REJECTED へ
 * - CLEARED / REJECTED は終端（以後の遷移は HandlingValidationError）
 * clearedAt は CLEARED 到達時に一度だけ設定し、以後は上書き・消去しない。
 */
export class CustomsDeclaration {
  private constructor(
    readonly declarationNumber: string,
    readonly handlingActivityId: number,
    private _status: CustomsStatus,
    readonly declaredAt: Date,
    private _clearedAt: Date | null,
    readonly remarks: string | null,
  ) {}

  get status(): CustomsStatus {
    return this._status;
  }

  get clearedAt(): Date | null {
    return this._clearedAt;
  }

  static register(params: RegisterCustomsDeclarationParams): CustomsDeclaration {
    if (params.declarationNumber.trim().length === 0) {
      throw new HandlingValidationError('申告番号は必須です');
    }
    if (Number.isNaN(params.declaredAt.getTime())) {
      throw new HandlingValidationError('申告日時が不正です');
    }
    return new CustomsDeclaration(
      params.declarationNumber,
      params.handlingActivityId,
      CustomsStatus.PENDING,
      params.declaredAt,
      null,
      params.remarks ?? null,
    );
  }

  static reconstruct(props: CustomsDeclarationProps): CustomsDeclaration {
    return new CustomsDeclaration(
      props.declarationNumber,
      props.handlingActivityId,
      props.status,
      props.declaredAt,
      props.clearedAt,
      props.remarks,
    );
  }

  /** 通関完了へ遷移する。clearedAt は初回のみ設定する */
  clear(clearedAt: Date = new Date()): void {
    this.assertCanTransitionTo(CustomsStatus.CLEARED);
    this._status = CustomsStatus.CLEARED;
    if (this._clearedAt === null) {
      this._clearedAt = clearedAt;
    }
  }

  /** 留置（税関保留）へ遷移する。CUSTOMS_HOLD 例外の自動登録契機となる */
  hold(): void {
    this.assertCanTransitionTo(CustomsStatus.HELD);
    this._status = CustomsStatus.HELD;
  }

  /** 通関不可へ遷移する */
  reject(): void {
    this.assertCanTransitionTo(CustomsStatus.REJECTED);
    this._status = CustomsStatus.REJECTED;
  }

  private assertCanTransitionTo(target: CustomsStatus): void {
    if (!ALLOWED_TRANSITIONS[this._status].includes(target)) {
      throw new HandlingValidationError(`通関状態を ${this._status} から ${target} へ遷移できません`);
    }
  }
}
