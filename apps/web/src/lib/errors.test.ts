import { ClientError } from 'graphql-request';
import { describe, expect, it } from 'vitest';
import { ApiError, messageFor, toApiError } from './errors';

const clientError = (status: number, errors: unknown[]) =>
  new ClientError({ status, errors, data: undefined, headers: new Headers() } as never, { query: '{ x }' });

describe('エラーの変換：通信エラーと業務エラーを区別し、文面は 1 つの表だけで決める', () => {
  it('業務エラーは extensions.code から、コードと不正な項目を取り出す', () => {
    const e = toApiError(
      clientError(200, [
        { message: 'VALIDATION_FAILED', extensions: { code: 'VALIDATION_FAILED', fields: [{ field: 'weightKg', reason: '数値ではない' }] } },
      ]),
    );
    expect(e).toBeInstanceOf(ApiError);
    expect(e.kind).toBe('business');
    expect(e.code).toBe('VALIDATION_FAILED');
    expect(e.fields).toEqual([{ field: 'weightKg', reason: '数値ではない' }]);
  });

  it('重複メールは、既存の荷主 ID を持つ', () => {
    const e = toApiError(clientError(200, [{ message: 'x', extensions: { code: 'DUPLICATE_EMAIL', existingShipperId: '01ARZ3NDEKTSV4RRFFQ69G5FAV' } }]));
    expect(e.code).toBe('DUPLICATE_EMAIL');
    expect(e.existingShipperId).toBe('01ARZ3NDEKTSV4RRFFQ69G5FAV');
  });

  it('401 は UNAUTHENTICATED（業務エラー）。ログインへ誘導するための区別', () => {
    const e = toApiError(clientError(401, [{ message: '認証が必要です', extensions: { code: 'UNAUTHENTICATED' } }]));
    expect(e.code).toBe('UNAUTHENTICATED');
  });

  it('ネットワークの失敗は、通信エラー（再送の案内）として業務エラーと区別する', () => {
    const e = toApiError(new TypeError('Failed to fetch'));
    expect(e.kind).toBe('network');
    expect(messageFor(e)).toContain('通信');
  });

  it('コードが無い・未知のコードの失敗は、内部の詳細を出さない一般のエラー', () => {
    const e = toApiError(clientError(500, [{ message: 'java.lang.NullPointerException at ...' }]));
    expect(e.kind).toBe('unknown');
    expect(messageFor(e)).not.toContain('NullPointer');
  });

  it('すべての業務コードに利用者向けの文面がある（表に載っていないコードは失敗にする）', () => {
    const codes = ['FORBIDDEN', 'VALIDATION_FAILED', 'DUPLICATE_EMAIL', 'SHIPPER_NOT_FOUND', 'IDEMPOTENCY_KEY_REUSED', 'INVALID_TRANSITION', 'NOT_FOUND', 'INVALID_INPUT', 'UNAUTHENTICATED'];
    for (const code of codes) {
      expect(messageFor(new ApiError('business', code)), code).not.toBe('');
      expect(messageFor(new ApiError('business', code)), code).not.toContain(code);
    }
  });

  it('認証の失敗とロックで同じ文面（US31）：認証エラーの文面は 1 つだけ', () => {
    expect(messageFor(new ApiError('business', 'UNAUTHENTICATED'))).toBe('ログインが必要です');
  });
});
