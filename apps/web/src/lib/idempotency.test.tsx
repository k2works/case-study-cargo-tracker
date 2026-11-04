import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useIdempotencyKey } from './idempotency';

describe('冪等性キー（コマンドの再送で二重に登録しないため。ADR-003）', () => {
  it('画面を開いた時点で 1 つ作り、再描画しても同じキーを使う（再押下・再送で同じキー）', () => {
    const { result, rerender } = renderHook(() => useIdempotencyKey());
    const first = result.current.key;
    rerender();
    rerender();
    expect(result.current.key).toBe(first);
    expect(first.length).toBeGreaterThan(10);
  });

  it('成功したら捨てる（reset すると新しいキー）。同じ入力を意図して 2 回登録するときのため', () => {
    const { result } = renderHook(() => useIdempotencyKey());
    const first = result.current.key;
    act(() => result.current.reset());
    expect(result.current.key).not.toBe(first);
  });

  it('別の画面（別のフック）は、別のキーを持つ', () => {
    const a = renderHook(() => useIdempotencyKey());
    const b = renderHook(() => useIdempotencyKey());
    expect(a.result.current.key).not.toBe(b.result.current.key);
  });
});
