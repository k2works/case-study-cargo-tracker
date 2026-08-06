import type { TrackingActivity } from '../model/tracking-activity.js';

/** 追跡レコードリポジトリ（出力ポート） */
export interface TrackingActivityRepository {
  save(activity: TrackingActivity): Promise<TrackingActivity>;
  findByTrackingNumber(trackingNumber: string): Promise<TrackingActivity | null>;
}
