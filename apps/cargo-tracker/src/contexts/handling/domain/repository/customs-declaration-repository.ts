import type { CustomsDeclaration } from '../model/customs-declaration.js';

/** 通関申告に紐づく荷役作業の文脈（発行イベント解決用） */
export interface CustomsHandlingContext {
  bookingId: string;
  /** 荷役作業の場所（UN/LOCODE）。CUSTOMS_HOLD 例外の発生場所として使う */
  location: string;
  /** 貨物の追跡番号（未発行なら null） */
  trackingNumber: string | null;
}

/** 通関申告リポジトリ（出力ポート） */
export interface CustomsDeclarationRepository {
  save(declaration: CustomsDeclaration): Promise<void>;
  update(declaration: CustomsDeclaration): Promise<void>;
  findByDeclarationNumber(declarationNumber: string): Promise<CustomsDeclaration | null>;
  /** 荷役作業に紐づく貨物文脈を取得する。荷役作業が存在しなければ null */
  findHandlingContext(handlingActivityId: number): Promise<CustomsHandlingContext | null>;
}
