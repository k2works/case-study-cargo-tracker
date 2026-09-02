import { Link } from 'react-router';
import { useAuthStore } from '@/shared/auth/authStore';
import { navigationFor } from '@/shared/ui/navigation';

/** S02 ダッシュボード。「今日の作業」からその日の入口へ行けるようにする。 */
export function DashboardPage() {
  const user = useAuthStore((state) => state.user);
  const items = user ? navigationFor(user.roles).filter((i) => i.path !== '/') : [];

  return (
    <section>
      <h1>ダッシュボード</h1>
      <h2>今日の作業</h2>
      <ul>
        {items.map((item) => (
          <li key={item.path}>
            <Link to={item.path}>{item.label}</Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
