import { pending, ready, type Pending } from './pending';

/** API が返すエラー本文（architecture_backend.md「例外と HTTP の対応」）。 */
export interface ApiErrorBody {
  readonly code: string;
  readonly message: string;
}

/**
 * 業務として意味のある失敗。
 *
 * 通信の失敗と業務の拒否を同じ型にすると、画面が「やり直せばよいのか」
 * 「入力を直すのか」を出し分けられない。
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: ApiErrorBody,
  ) {
    super(body.message);
    this.name = 'ApiError';
  }
}

const BASE = '/api/v1';

async function parseBody(response: Response): Promise<unknown> {
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

/** 問い合わせ。202 は「反映中」として返し、失敗にしない。 */
export async function queryClient<T>(path: string): Promise<Pending<T>> {
  const response = await fetch(`${BASE}${path}`, {
    headers: { Accept: 'application/json' },
    cache: 'no-store',
  });
  const body = await parseBody(response);

  if (response.status === 202) {
    const message =
      (body as { message?: string } | null)?.message ?? '反映までしばらくお待ちください';
    return pending(message);
  }
  if (!response.ok) {
    throw new ApiError(response.status, body as ApiErrorBody);
  }
  return ready(body as T);
}

/** 状態を変える操作。409 / 422 は本文つきで投げ、画面が理由を出せるようにする。 */
export async function commandClient<T>(path: string, payload: unknown): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(payload),
    cache: 'no-store',
  });
  const body = await parseBody(response);

  if (!response.ok) {
    throw new ApiError(response.status, body as ApiErrorBody);
  }
  return body as T;
}
