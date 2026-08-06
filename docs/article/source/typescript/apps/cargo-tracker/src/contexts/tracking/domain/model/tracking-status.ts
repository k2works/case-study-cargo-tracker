/** 追跡状態（9 段階の追跡フェーズ。IT5 は荷役由来 5 状態、例外系は IT6） */
export const TrackingStatus = {
  NOT_RECEIVED: 'NOT_RECEIVED',
  RECEIVED: 'RECEIVED',
  LOADED: 'LOADED',
  ONBOARD_CARRIER: 'ONBOARD_CARRIER',
  UNLOADED: 'UNLOADED',
  AWAITING_CLAIM: 'AWAITING_CLAIM',
  CLAIMED: 'CLAIMED',
  EXCEPTION: 'EXCEPTION',
  UNKNOWN: 'UNKNOWN',
} as const;
export type TrackingStatus = (typeof TrackingStatus)[keyof typeof TrackingStatus];

export const TRACKING_STATUS_LABELS: Record<TrackingStatus, string> = {
  NOT_RECEIVED: '受領待ち',
  RECEIVED: '受領済',
  LOADED: '積込済',
  ONBOARD_CARRIER: '輸送中',
  UNLOADED: '荷降し済',
  AWAITING_CLAIM: '引取待ち',
  CLAIMED: '引取済',
  EXCEPTION: '例外発生',
  UNKNOWN: '不明',
};

export function isTrackingStatus(value: string): value is TrackingStatus {
  return Object.values(TrackingStatus).includes(value as TrackingStatus);
}

/**
 * 輸送フェーズの進行順序（受領前 → 引取済）。EXCEPTION / UNKNOWN は輸送フェーズ外なので順序に含めない。
 * 例外解決後の復帰先を「より進んだ状態」で決めるための比較に用いる。
 */
const TRANSPORT_PHASE_ORDER: readonly TrackingStatus[] = [
  TrackingStatus.NOT_RECEIVED,
  TrackingStatus.RECEIVED,
  TrackingStatus.LOADED,
  TrackingStatus.ONBOARD_CARRIER,
  TrackingStatus.UNLOADED,
  TrackingStatus.AWAITING_CLAIM,
  TrackingStatus.CLAIMED,
];

/**
 * 輸送フェーズの進行度（大きいほど進んでいる）。
 * EXCEPTION / UNKNOWN など順序外の状態は -1 を返す。
 */
export function transportPhaseRank(status: TrackingStatus): number {
  return TRANSPORT_PHASE_ORDER.indexOf(status);
}
