/**
 * 通関留置イベントの契約（共有カーネル）。
 * 発行側（Handling）と購読側（Tracking）で型を 1 箇所に集約する。
 * HELD 遷移成功後にコミット後 emit され、Tracking が CUSTOMS_HOLD 例外を登録する（ADR-005/009/010）。
 */

/** 通関留置イベント名（EventEmitter2 のイベントキー） */
export const CUSTOMS_HELD_EVENT = 'customs.held';

/** 通関留置イベントのペイロード契約（Handling → Tracking） */
export interface CustomsHeldPayload {
  bookingId: string;
  /** 貨物の追跡番号。未発行など解決できない場合は null（Tracking 側は登録をスキップする） */
  trackingNumber: string | null;
  declarationNumber: string;
  /** 留置が発生した場所（UN/LOCODE）。CUSTOMS_HOLD 例外の発生場所として使う */
  location: string;
}
