import { describe, expect, it } from 'vitest';
import { HandlingValidationError } from './handling-validation-error.js';
import { HandlingType, HANDLING_TYPE_LABELS } from './handling-type.js';

describe('HandlingType（荷役種別）', () => {
  it.each([
    ['RECEIVE', false],
    ['LOAD', true],
    ['UNLOAD', true],
    ['CUSTOMS', false],
    ['CLAIM', false],
  ])('%s の VoyageNumber 必須は %s', (raw, required) => {
    expect(HandlingType.of(raw).requiresVoyageNumber()).toBe(required);
  });

  it.each([
    ['RECEIVE', false],
    ['LOAD', true],
    ['UNLOAD', true],
    ['CUSTOMS', false],
    ['CLAIM', false],
  ])('%s の場所不一致時 MISROUTED 判定は %s（それ以外は警告）', (raw, misroutes) => {
    expect(HandlingType.of(raw).misroutesOnMismatch()).toBe(misroutes);
  });

  it('CLAIM は引取種別である', () => {
    expect(HandlingType.of('CLAIM').isClaimType()).toBe(true);
    expect(HandlingType.of('LOAD').isClaimType()).toBe(false);
  });

  it('不正な種別はエラー', () => {
    expect(() => HandlingType.of('INVALID')).toThrow(HandlingValidationError);
  });

  it('日本語ラベルを持つ', () => {
    expect(HANDLING_TYPE_LABELS.RECEIVE).toBe('受領');
    expect(HANDLING_TYPE_LABELS.CLAIM).toBe('引取');
  });
});
