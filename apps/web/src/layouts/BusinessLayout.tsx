import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { navFor } from '@/features/auth';
import { useCurrentUser } from '@/providers/AuthProvider';

const ROLE_LABELS = { Shipper: '荷主', Sales: '営業担当者', RouteDesigner: '経路設計者', TrackingManager: '追跡管理者', HandlingWorker: '荷役作業員', Accountant: '経理担当者' } as const;

/** 業務画面の枠：ロールに応じたメニュー。公開の追跡照会（Bolt 8）は別のレイアウト・別のバンドルにする（FE-04）。 */
export function BusinessLayout({ children }: { children: ReactNode }) {
  const user = useCurrentUser();
  return (
    <div className="layout">
      <header>
        <strong>国際貨物輸送管理</strong>
        {user && <span>{ROLE_LABELS[user.role]}</span>}
        {user && (
          <nav aria-label="メインメニュー">
            <ul>
              {navFor(user.role).map((item) => (
                <li key={item.to}>
                  <Link to={item.to}>{item.label}</Link>
                </li>
              ))}
            </ul>
          </nav>
        )}
      </header>
      {children}
    </div>
  );
}
