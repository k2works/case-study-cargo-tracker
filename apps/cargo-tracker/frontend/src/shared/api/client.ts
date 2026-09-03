import { useAuthStore } from '../auth/authStore';
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

/**
 * 資格情報つきのヘッダを組む。
 *
 * <p>認証は Gateway が見る（ADR-0001 決定 4）。ここで付け忘れると、ログインは
 * できるのに業務画面がすべて 401 になる。単体テストは fetch を差し替えるので、
 * 付け忘れに気づけない。だから「トークンがあれば送る」ことを検査で固定する。</p>
 */
function headersWithAuth(base: Record<string, string>): Record<string, string> {
  const token = useAuthStore.getState().user?.token;
  return token ? { ...base, Authorization: `Bearer ${token}` } : base;
}

/**
 * 資格情報が通らなくなったら認証を捨てる。
 *
 * <p>昼休みを挟むと期限が切れる。そのとき「取得できませんでした」とだけ出ると、
 * 利用者は再ログインすればよいと分からず「システムが壊れた」と受け取る。
 * 認証を捨てれば画面のガードがログインへ送る。</p>
 */
function forgetAuthenticationOn(status: number): void {
  if (status === 401) {
    useAuthStore.getState().logout();
  }
}

/** 問い合わせ。202 は「反映中」として返し、失敗にしない。 */
export async function queryClient<T>(path: string): Promise<Pending<T>> {
  const response = await fetch(`${BASE}${path}`, {
    headers: headersWithAuth({ Accept: 'application/json' }),
    cache: 'no-store',
  });
  const body = await parseBody(response);

  forgetAuthenticationOn(response.status);

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
    headers: headersWithAuth({
      'Content-Type': 'application/json',
      Accept: 'application/json',
    }),
    body: JSON.stringify(payload),
    cache: 'no-store',
  });
  const body = await parseBody(response);

  forgetAuthenticationOn(response.status);

  if (!response.ok) {
    throw new ApiError(response.status, body as ApiErrorBody);
  }
  return body as T;
}
