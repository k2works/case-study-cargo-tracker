import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth/contexts/AuthContext';

export default function Navigation() {
  const { username, role, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login');
  }

  return (
    <nav aria-label="メインナビゲーション">
      <header>
        <span>Cargo Tracker</span>
        <span>{username}（{role}）</span>
        <button type="button" onClick={handleLogout}>ログアウト</button>
      </header>
      <ul>
        <li><NavLink to="/">ダッシュボード</NavLink></li>
        <li><NavLink to="/voyages">航海スケジュール</NavLink></li>
      </ul>
    </nav>
  );
}
