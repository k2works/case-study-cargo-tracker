import { describe, it, expect } from 'vitest';
import { allowedNextStatuses, transportStatusLabel } from './trackingApi';

describe('trackingApi', () => {
  describe('allowedNextStatuses（バックエンド TransportStatusTransition と整合）', () => {
    it('NOT_RECEIVED から RECEIVED / MISROUTED / EXCEPTION のみ許可', () => {
      expect(allowedNextStatuses('NOT_RECEIVED')).toEqual(['RECEIVED', 'MISROUTED', 'EXCEPTION']);
    });

    it('UNLOADED は LOADED への積み替え + AWAITING_CLAIM + MISROUTED + EXCEPTION', () => {
      expect(allowedNextStatuses('UNLOADED')).toEqual([
        'LOADED',
        'AWAITING_CLAIM',
        'MISROUTED',
        'EXCEPTION',
      ]);
    });

    it('DELIVERED からは遷移なし（終端）', () => {
      expect(allowedNextStatuses('DELIVERED')).toEqual([]);
    });

    it('H5: MISROUTED から RECEIVED / LOADED / IN_TRANSIT への救済遷移', () => {
      expect(allowedNextStatuses('MISROUTED')).toEqual(['RECEIVED', 'LOADED', 'IN_TRANSIT']);
    });

    it('EXCEPTION からは RECEIVED / LOADED / IN_TRANSIT のみ復帰', () => {
      expect(allowedNextStatuses('EXCEPTION')).toEqual(['RECEIVED', 'LOADED', 'IN_TRANSIT']);
    });
  });

  describe('transportStatusLabel', () => {
    it('日本語ラベルを返す', () => {
      expect(transportStatusLabel('NOT_RECEIVED')).toBe('未受領');
      expect(transportStatusLabel('IN_TRANSIT')).toBe('輸送中');
      expect(transportStatusLabel('DELIVERED')).toBe('配送完了');
      expect(transportStatusLabel('MISROUTED')).toBe('誤配送');
    });
  });
});
