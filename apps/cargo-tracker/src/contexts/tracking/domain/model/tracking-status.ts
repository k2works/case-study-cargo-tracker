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
