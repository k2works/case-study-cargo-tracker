import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';

/** 通関申告の 1 行（Read Model・クエリ専用） */
export interface CustomsDeclarationSummary {
  declarationNumber: string;
  handlingActivityId: number;
  status: string;
  declaredAt: Date;
  clearedAt: Date | null;
  remarks: string | null;
}

/** 追跡番号で解決した貨物の通関状態ビュー */
export interface CustomsView {
  bookingId: string;
  declarations: CustomsDeclarationSummary[];
  /** 新規申告の紐付け先（最新の荷役作業）。荷役作業がなければ null */
  latestHandlingActivityId: number | null;
}

/**
 * 通関状態クエリサービス（CQRS 読み取り側）。
 * 追跡番号から貨物を特定し、通関申告一覧と新規申告の紐付け先（最新荷役作業）を返す。
 */
export class CustomsQueryService {
  constructor(private readonly db: AppDatabase) {}

  async findByTrackingNumber(trackingNumber: string): Promise<CustomsView | null> {
    const cargo = await this.db
      .selectFrom('cargo')
      .select('bookingId')
      .where('trackingNumber', '=', trackingNumber)
      .executeTakeFirst();
    if (cargo === undefined) {
      return null;
    }
    const latest = await this.db
      .selectFrom('handling_activity')
      .select('id')
      .where('bookingId', '=', cargo.bookingId)
      .orderBy('eventCompletionTime', 'desc')
      .executeTakeFirst();
    const rows = await this.db
      .selectFrom('customs_declaration')
      .innerJoin('handling_activity', 'handling_activity.id', 'customs_declaration.handlingActivityId')
      .select([
        'customs_declaration.declarationNumber as declarationNumber',
        'customs_declaration.handlingActivityId as handlingActivityId',
        'customs_declaration.status as status',
        'customs_declaration.declaredAt as declaredAt',
        'customs_declaration.clearedAt as clearedAt',
        'customs_declaration.remarks as remarks',
      ])
      .where('handling_activity.bookingId', '=', cargo.bookingId)
      .orderBy('customs_declaration.declaredAt', 'desc')
      .execute();
    return {
      bookingId: cargo.bookingId,
      latestHandlingActivityId: latest?.id ?? null,
      declarations: rows.map((row) => ({
        declarationNumber: row.declarationNumber,
        handlingActivityId: row.handlingActivityId,
        status: row.status,
        declaredAt: new Date(row.declaredAt),
        clearedAt: row.clearedAt === null ? null : new Date(row.clearedAt),
        remarks: row.remarks,
      })),
    };
  }
}
