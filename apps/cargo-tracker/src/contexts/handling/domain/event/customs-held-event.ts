import type { CustomsHeldPayload } from '../../../../shared/contracts/customs-held.contract.js';

// イベント名は共有契約（shared/contracts）を正とし、後方互換のため再エクスポートする。
export { CUSTOMS_HELD_EVENT } from '../../../../shared/contracts/customs-held.contract.js';

/**
 * 通関留置イベント（Handling → Tracking）。
 * 通関申告が HELD に遷移したとき、Tracking が CUSTOMS_HOLD 例外を自動登録する契機となる。
 * コミット後発行・冪等リスナー（ADR-005/009/010）。
 */
export class CustomsHeldEvent implements CustomsHeldPayload {
  constructor(
    readonly bookingId: string,
    readonly trackingNumber: string | null,
    readonly declarationNumber: string,
    readonly location: string,
  ) {}
}
