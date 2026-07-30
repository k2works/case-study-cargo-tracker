import type { HandlingActivity } from '../model/handling-activity.js';

/** 荷役作業リポジトリ（出力ポート） */
export interface HandlingActivityRepository {
  save(activity: HandlingActivity): Promise<HandlingActivity>;
  findByBookingId(bookingId: string): Promise<HandlingActivity[]>;
}
