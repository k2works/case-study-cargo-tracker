import { ClientError } from 'graphql-request';

/** 通信エラー（再送の案内）、業務エラー（入力の修正の案内）、それ以外を区別する（FE 7.1）。 */
export type ApiErrorKind = 'network' | 'business' | 'unknown';

export type FieldError = { field: string; reason: string };

export class ApiError extends Error {
  constructor(
    readonly kind: ApiErrorKind,
    readonly code: string | null = null,
    readonly fields: FieldError[] = [],
    readonly existingShipperId: string | null = null,
  ) {
    super(code ?? kind);
  }
}

/** 業務エラーのコードを、利用者向けの文面に写す表。**画面ごとに文面を書かない**（この表が単一の出所）。 */
const MESSAGES: Record<string, string> = {
  FORBIDDEN: 'この操作を行う権限がありません',
  VALIDATION_FAILED: '入力内容を確認してください',
  DUPLICATE_EMAIL: '同じメールアドレスの荷主が既に登録されています',
  SHIPPER_NOT_FOUND: '荷主が見つかりません',
  IDEMPOTENCY_KEY_REUSED: '同じ送信の内容が前回と異なります。画面を開き直してもう一度操作してください',
  INVALID_TRANSITION: 'この予約は、現在の状態ではその操作ができません',
  NOT_FOUND: '対象が見つかりません',
  INVALID_INPUT: '入力内容を確認してください',
  UNAUTHENTICATED: 'ログインが必要です',
};

export function messageFor(error: ApiError): string {
  if (error.kind === 'network') return '通信に失敗しました。接続を確認して、もう一度お試しください';
  if (error.kind === 'business' && error.code && MESSAGES[error.code]) return MESSAGES[error.code]!;
  return '予期しないエラーが発生しました。時間をおいて、もう一度お試しください';
}

type GqlError = { message?: string; extensions?: { code?: string; fields?: FieldError[]; existingShipperId?: string } };

/** graphql-request の失敗を ApiError にする。サーバー内部の詳細（例外メッセージなど）は利用者に出さない。 */
export function toApiError(cause: unknown): ApiError {
  if (cause instanceof ApiError) return cause;
  if (cause instanceof ClientError) {
    const first = (cause.response.errors as GqlError[] | undefined)?.[0];
    const code = first?.extensions?.code;
    if (code) return new ApiError('business', code, first.extensions?.fields ?? [], first.extensions?.existingShipperId ?? null);
    return new ApiError('unknown');
  }
  if (cause instanceof TypeError) return new ApiError('network');
  return new ApiError('unknown');
}
