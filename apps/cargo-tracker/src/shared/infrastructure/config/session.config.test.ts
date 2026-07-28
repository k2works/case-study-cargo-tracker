import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { resolveSessionSecret } from './session.config.js';

describe('resolveSessionSecret', () => {
  const original = { ...process.env };

  beforeEach(() => {
    delete process.env.SESSION_SECRET;
    delete process.env.NODE_ENV;
  });

  afterEach(() => {
    process.env = { ...original };
  });

  it('SESSION_SECRET があればそれを返す', () => {
    process.env.SESSION_SECRET = 'my-secret';
    expect(resolveSessionSecret()).toBe('my-secret');
  });

  it('開発時は固定フォールバック値を返す', () => {
    expect(resolveSessionSecret()).toBe('cargo-tracker-dev-secret');
  });

  it('本番で未設定なら例外を送出する', () => {
    process.env.NODE_ENV = 'production';
    expect(() => resolveSessionSecret()).toThrow();
  });
});
