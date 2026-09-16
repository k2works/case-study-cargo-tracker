import { commandClient, queryClient } from '@/shared/api/client';
import type { Pending } from '@/shared/api/pending';
import type { CargoType } from '@/shared/domain/cargoType';

/**
 * ルート候補 1 件（S13 / US01 §受入基準 3）。
 *
 * 経由港・所要日数・概算料金・航海番号——この 4 つが読めなければ、営業担当者は
 * 荷主に案を説明できない。
 */
export interface QuotationCandidateView {
  readonly candidateSeq: number;
  /** 航海番号の並び（`V-MOL-001 > V-ONE-002`）。**区切りはサーバが決める。** */
  readonly voyageNumbers: string;
  /**
   * 経由港の並び（`JPTYO > SGSIN > USNYC`）。**候補ごとに違う。**
   *
   * 出発地と目的地だけを画面で繋ぐと、どの候補も同じ経路に見え、営業担当者は
   * 案を選び分けられない。列が無かったころの見積では `null` になる。
   */
  readonly ports: string | null;
  readonly transitDays: number;
  readonly estimatedCost: number;
  readonly currency: string;
  /** 希望期限からの超過日数。**0 なら間に合う**（画面で数え直さない）。 */
  readonly overdueDays: number;
}

/** 見積の詳細（S13 / US01）。 */
export interface QuotationView {
  readonly quotationId: string;
  readonly originUnLocode: string;
  readonly destinationUnLocode: string;
  readonly arrivalDeadline: string;
  readonly cargoType: CargoType;
  readonly weightKg: number;
  /** いちばん安い候補の概算。**候補が無ければ 0 円。** */
  readonly estimatedAmount: number;
  readonly currency: string;
  readonly validUntil: string;
  /**
   * 希望期限に間に合う候補があるか。
   *
   * **サーバが数える**（US01 §受入基準 5）。画面で数え直すと、判断が 2 か所に
   * 書かれて片方だけが直る。
   */
  readonly hasDeadlineMeetingCandidate: boolean;
  readonly createdBy: string;
  readonly createdAt: string;
  readonly candidates: readonly QuotationCandidateView[];
}

/** 見積の入力（5 項目 + 危険物申告）。 */
export interface CreateQuotationInput {
  readonly originUnLocode: string;
  readonly destinationUnLocode: string;
  readonly arrivalDeadline: string;
  readonly cargoType: CargoType;
  /** 重量。**数値で送る**——予約（S21）と形を揃える。 */
  readonly weightKg: number;
  readonly hazardousImoClass: string | null;
  readonly hazardousUnNumber: string | null;
}

/**
 * 見積を作る（S12 / US01）。
 *
 * **見積番号はサーバが採る。** 画面で採ると、押し直しが二つ目の見積になる。
 */
export function createQuotation(input: CreateQuotationInput): Promise<{ quotationId: string }> {
  return commandClient('/booking/quotations', input);
}

/** 見積 1 件（S13）。**投影がまだなら「反映中」を返す。** */
export function fetchQuotation(quotationId: string): Promise<Pending<QuotationView>> {
  return queryClient(`/booking/quotations/${encodeURIComponent(quotationId)}`);
}
