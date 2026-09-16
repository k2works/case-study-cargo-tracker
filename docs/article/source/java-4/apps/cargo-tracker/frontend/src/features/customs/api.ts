import { commandClient, queryClient } from '@/shared/api/client';
import type { Pending } from '@/shared/api/pending';

/** 通関状態（domain-model.md の要素表が正典）。 */
export type CustomsStatus = 'PENDING' | 'CLEARED' | 'HELD' | 'REJECTED';

export interface CustomsDeclarationView {
  readonly declarationNumber: string;
  readonly trackingNumber: string;
  readonly bookingId: string;
  readonly status: CustomsStatus;
  /** 呼び名はサーバが返す。**画面で対応表を持たない**——2 か所になると片方だけ直る。 */
  readonly statusLabel: string;
  readonly declaredAt: string;
  readonly lastStatusChangedAt: string;
  readonly lastHeldAt: string | null;
  /** 留置してからの営業日数。**サーバが読むときに数える**（留置中は日が経つだけで変わる）。 */
  readonly heldBusinessDays: number;
  /** 督促の対象か。**判定はサーバが持つ**（画面で数え直さない）。 */
  readonly overdue: boolean;
  readonly lastReason: string | null;
  readonly changedBy: string | null;
}

export interface CustomsHistoryEntryView {
  /** `REGISTERED` / `STATUS_CHANGED` / `CLEARANCE_NOTIFIED`。 */
  readonly kind: string;
  readonly previousStatus: CustomsStatus | null;
  readonly status: CustomsStatus | null;
  readonly statusLabel: string | null;
  readonly reason: string | null;
  readonly changedBy: string | null;
  readonly changedAt: string;
}

export interface CustomsSearchCondition {
  readonly includeCleared: boolean;
  readonly trackingNumber: string;
  readonly status: CustomsStatus | '';
  readonly overdueOnly: boolean;
}

/**
 * 通関申告の一覧（S52 / US29 §受入基準 7）。
 *
 * <p>**既定で通関済を外す。** 決着したものが混ざると、一覧全体が「まだ手を入れる
 * 場所」に見えなくなる。並びはサーバが決める（留置営業日数が多い順）。</p>
 */
export function fetchCustomsDeclarations(
  condition: CustomsSearchCondition,
): Promise<Pending<{ items: CustomsDeclarationView[]; total: number; truncated: boolean }>> {
  const query = new URLSearchParams({
    includeCleared: condition.includeCleared ? 'true' : 'false',
    overdueOnly: condition.overdueOnly ? 'true' : 'false',
  });
  if (condition.trackingNumber.trim() !== '') {
    query.set('trackingNumber', condition.trackingNumber.trim().toUpperCase());
  }
  if (condition.status !== '') {
    query.set('status', condition.status);
  }
  return queryClient(`/handling/customs-declarations?${query.toString()}`);
}

/** 通関申告 1 件（S53）。 */
export function fetchCustomsDeclaration(
  declarationNumber: string,
): Promise<Pending<CustomsDeclarationView>> {
  return queryClient(
    `/handling/customs-declarations/${encodeURIComponent(declarationNumber)}`,
  );
}

/** 状態の変更履歴（S53 / US29 §受入基準 8）。 */
export function fetchCustomsHistory(
  declarationNumber: string,
): Promise<Pending<{ items: CustomsHistoryEntryView[] }>> {
  return queryClient(
    `/handling/customs-declarations/${encodeURIComponent(declarationNumber)}/history`,
  );
}

/** 通関申告を登録する（S53 / 荷役ロール）。 */
export function registerCustomsDeclaration(input: {
  declarationNumber: string;
  trackingNumber: string;
  declaredAt: string;
}): Promise<void> {
  return commandClient('/handling/customs-declarations', input);
}

/** 通関状態を更新する（S53 / 追跡ロール）。**理由は必須。** */
export function updateCustomsStatus(
  declarationNumber: string,
  input: { status: CustomsStatus; reason: string },
): Promise<void> {
  return commandClient(
    `/handling/customs-declarations/${encodeURIComponent(declarationNumber)}/status`,
    input,
  );
}

/**
 * 選べる通関状態（S53）。
 *
 * <p>**審査中へは戻せない。** 集約が同じ状態への更新と決着後の更新を断るので、
 * 選択肢にも出さない——押せるのに断られる操作を並べると、押した人は
 * 「壊れている」と読む。</p>
 */
export const UPDATABLE_STATUSES: ReadonlyArray<{ value: CustomsStatus; label: string }> = [
  { value: 'CLEARED', label: '通関済' },
  { value: 'HELD', label: '留置' },
  { value: 'REJECTED', label: '不可' },
];

/**
 * 決着した通関状態（S53 で更新の口を出さない）。
 *
 * <p>**サーバと同じ述語を写す**（`CustomsStatus#unsettled` の裏）。ここに
 * 書き写した判定が本番と別に育つのを避けるため、**画面はこの 1 か所だけ**を
 * 見る（IT12 レビュー 高。決着済でもフォームが出て、押すと断られていた）。</p>
 */
export const CUSTOMS_SETTLED_STATUSES: ReadonlyArray<CustomsStatus> = ['CLEARED', 'REJECTED'];

/** 絞り込みに使う通関状態（すべてを含む）。 */
export const SEARCHABLE_STATUSES: ReadonlyArray<{ value: CustomsStatus | ''; label: string }> = [
  { value: '', label: 'すべて' },
  { value: 'PENDING', label: '審査中' },
  { value: 'HELD', label: '留置' },
  { value: 'CLEARED', label: '通関済' },
  { value: 'REJECTED', label: '不可' },
];

/**
 * 督促の対象（留置 3 営業日超）の件数（S02 / US29 §受入基準 6）。
 *
 * <p><b>一覧と同じ判定で数える。</b> 違う判定にすると、件数をたどった先に
 * 何も無い、が起きる。だから一覧の絞りをそのまま使う。</p>
 */
export function fetchOverdueCustomsHolds(): Promise<Pending<{ total: number }>> {
  return queryClient('/handling/customs-declarations?includeCleared=true&overdueOnly=true');
}
