import { describe, expect, it } from 'vitest';
import { Role } from '../../shared/domain/model/role.js';
import { visibleNavItems } from './nav-items.js';

describe('visibleNavItems（ロール別ナビ表示制御）', () => {
  it('営業担当者には見積管理・荷主登録・貨物予約が表示される', () => {
    const labels = visibleNavItems([Role.SALES]).map((i) => i.label);
    expect(labels).toContain('見積管理');
    expect(labels).toContain('荷主登録');
    expect(labels).toContain('貨物予約');
    expect(labels).not.toContain('請求管理');
    expect(labels).not.toContain('航路管理');
  });

  it('経理担当者には請求管理のみ業務メニューが表示される', () => {
    const labels = visibleNavItems([Role.BILLING]).map((i) => i.label);
    expect(labels).toContain('ダッシュボード');
    expect(labels).toContain('請求管理');
    expect(labels).not.toContain('見積管理');
    expect(labels).not.toContain('荷役管理');
  });

  it('経路設計者には貨物予約・貨物追跡・航路管理が表示される', () => {
    const labels = visibleNavItems([Role.ROUTE_DESIGNER]).map((i) => i.label);
    expect(labels).toContain('航路管理');
    expect(labels).toContain('貨物予約');
    expect(labels).not.toContain('請求管理');
  });

  it('ダッシュボードは全ロール共通で表示される', () => {
    for (const role of Object.values(Role)) {
      const labels = visibleNavItems([role]).map((i) => i.label);
      expect(labels).toContain('ダッシュボード');
    }
  });
});
