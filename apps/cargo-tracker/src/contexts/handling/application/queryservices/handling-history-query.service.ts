import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';

/** 荷役履歴の 1 行（Read Model・クエリ専用） */
export interface HandlingActivitySummary {
  id: number;
  bookingId: string;
  eventType: string;
  eventCompletionTime: Date;
  locationUnlocode: string;
  voyageNumber: string | null;
  operatorName: string | null;
}

/**
 * 荷役履歴クエリサービス（HandlingActivityHistory Read Model・CQRS 読み取り側）。
 * 集約と切り離したクエリ専用モデルとして荷役作業履歴・通関状態を提供する。
 */
export class HandlingHistoryQueryService {
  constructor(private readonly db: AppDatabase) {}

  /** 荷役履歴一覧。bookingId 指定時はその貨物のみ、未指定は全件（新しい順） */
  async list(bookingId?: string): Promise<HandlingActivitySummary[]> {
    let query = this.db
      .selectFrom('handling_activity')
      .select(['id', 'bookingId', 'eventType', 'eventCompletionTime', 'locationUnlocode', 'voyageNumber', 'operatorName'])
      .orderBy('eventCompletionTime', 'desc');
    if (bookingId !== undefined && bookingId !== '') {
      query = query.where('bookingId', '=', bookingId);
    }
    const rows = await query.execute();
    return rows.map((row) => ({ ...row, eventCompletionTime: new Date(row.eventCompletionTime) }));
  }

  /** 最後に完了した荷役イベント */
  async mostRecentlyCompletedEvent(bookingId: string): Promise<HandlingActivitySummary | null> {
    const rows = await this.list(bookingId);
    return rows[0] ?? null;
  }

  /** 通関申告が CLEARED 済みか（CLEARED まで CLAIM 不可の前提条件判定） */
  async isCustomsCleared(bookingId: string): Promise<boolean> {
    const row = await this.db
      .selectFrom('customs_declaration')
      .innerJoin('handling_activity', 'handling_activity.id', 'customs_declaration.handlingActivityId')
      .select('customs_declaration.id')
      .where('handling_activity.bookingId', '=', bookingId)
      .where('customs_declaration.status', '=', 'CLEARED')
      .executeTakeFirst();
    return row !== undefined;
  }
}
