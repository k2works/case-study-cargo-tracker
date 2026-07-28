import { describe, expect, it } from 'vitest';
import {
  MAX_FAILED_ATTEMPTS,
  isLockedByAttempts,
  nextFailedAttempts,
} from './account-lock-policy.js';

describe('account-lock-policy', () => {
  it('閾値は 5 回', () => {
    expect(MAX_FAILED_ATTEMPTS).toBe(5);
  });

  it.each([0, 1, 4])('内側境界 %i 回はロックされない', (n) => {
    expect(isLockedByAttempts(n)).toBe(false);
  });

  it.each([5, 6])('閾値以上 %i 回はロックされる', (n) => {
    expect(isLockedByAttempts(n)).toBe(true);
  });

  it('nextFailedAttempts は 1 加算する', () => {
    expect(nextFailedAttempts(4)).toBe(5);
  });
});
