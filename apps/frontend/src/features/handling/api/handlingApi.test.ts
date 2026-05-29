import { describe, it, expect } from 'vitest';
import {
  handlingTypeLabel,
  requiresClaimVerification,
  requiresVoyageNumber,
} from './handlingApi';

describe('handlingApi', () => {
  describe('handlingTypeLabel', () => {
    it('5 種別の日本語ラベルを返す', () => {
      expect(handlingTypeLabel('RECEIVE')).toBe('受領');
      expect(handlingTypeLabel('LOAD')).toBe('積込');
      expect(handlingTypeLabel('UNLOAD')).toBe('荷降し');
      expect(handlingTypeLabel('CLAIM')).toBe('引取');
      expect(handlingTypeLabel('CUSTOMS')).toBe('税関通過');
    });
  });

  describe('requiresVoyageNumber', () => {
    it('LOAD / UNLOAD で true', () => {
      expect(requiresVoyageNumber('LOAD')).toBe(true);
      expect(requiresVoyageNumber('UNLOAD')).toBe(true);
    });
    it('それ以外は false', () => {
      expect(requiresVoyageNumber('RECEIVE')).toBe(false);
      expect(requiresVoyageNumber('CLAIM')).toBe(false);
      expect(requiresVoyageNumber('CUSTOMS')).toBe(false);
    });
  });

  describe('requiresClaimVerification', () => {
    it('CLAIM で true、それ以外は false', () => {
      expect(requiresClaimVerification('CLAIM')).toBe(true);
      expect(requiresClaimVerification('RECEIVE')).toBe(false);
      expect(requiresClaimVerification('LOAD')).toBe(false);
    });
  });
});
