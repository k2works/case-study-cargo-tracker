import { Link } from 'react-router-dom';
import { useAuth } from '../../auth/contexts/AuthContext';

interface CardProps {
  to: string;
  title: string;
  description: string;
  icon: string;
  color: string;
}

function DashboardCard({ to, title, description, icon, color }: Readonly<CardProps>) {
  return (
    <Link
      to={to}
      className="group block rounded-xl border border-gray-100 bg-white p-6 shadow-sm hover:shadow-lg transition-all duration-200 hover:-translate-y-0.5"
    >
      <div className={`inline-flex items-center justify-center w-12 h-12 rounded-lg ${color} mb-4`}>
        <span className="text-2xl" aria-hidden="true">{icon}</span>
      </div>
      <h2 className="text-base font-semibold text-gray-900 mb-1 group-hover:text-blue-600 transition-colors">
        {title}
      </h2>
      <p className="text-sm text-gray-500 leading-relaxed">{description}</p>
    </Link>
  );
}

interface SectionProps {
  title: string;
  children: React.ReactNode;
}

function Section({ title, children }: Readonly<SectionProps>) {
  return (
    <section className="mb-8">
      <h2 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">
        {title}
      </h2>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {children}
      </div>
    </section>
  );
}

export default function DashboardPage() {
  const { username, role } = useAuth();
  const hasRole = (...roles: string[]) => roles.includes(role ?? '');

  const isSalesOrAdmin = hasRole('ROLE_ADMIN', 'ROLE_SALES');
  const isRoutingOrAdmin = hasRole('ROLE_ADMIN', 'ROLE_ROUTING');
  const isTrackerOrAdmin = hasRole('ROLE_ADMIN', 'ROLE_TRACKER');
  const isHandlerOrAdmin = hasRole('ROLE_ADMIN', 'ROLE_HANDLER');
  const isAccountantOrAdmin = hasRole('ROLE_ADMIN', 'ROLE_ACCOUNTANT');

  return (
    <div className="p-8 max-w-5xl mx-auto">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">ダッシュボード</h1>
        <p className="text-gray-500 mt-1">
          ようこそ、{username ?? 'ゲスト'} さん
        </p>
      </div>

      {isSalesOrAdmin && (
        <Section title="営業・予約">
          <DashboardCard
            to="/bookings"
            title="予約管理"
            description="予約の確認・経路設計依頼・確定・追跡番号発行"
            icon="📦"
            color="bg-blue-50"
          />
          <DashboardCard
            to="/shippers"
            title="荷主管理"
            description="荷主の登録・確認・更新"
            icon="🏢"
            color="bg-indigo-50"
          />
          <DashboardCard
            to="/quotes"
            title="見積管理"
            description="見積の作成・確認・承認"
            icon="📋"
            color="bg-violet-50"
          />
        </Section>
      )}

      {isRoutingOrAdmin && (
        <Section title="経路・航海">
          <DashboardCard
            to="/voyages"
            title="航海スケジュール"
            description="航海の登録・編集・スケジュール確認"
            icon="🚢"
            color="bg-cyan-50"
          />
          <DashboardCard
            to="/routing/design"
            title="経路設計"
            description="経路設計待ち予約の確認・経路候補算出・紐付け"
            icon="🗺️"
            color="bg-sky-50"
          />
        </Section>
      )}

      {isTrackerOrAdmin && (
        <Section title="追跡・例外">
          <DashboardCard
            to="/tracking"
            title="追跡管理"
            description="追跡番号で貨物の現在地・ステータスを確認"
            icon="📍"
            color="bg-orange-50"
          />
          <DashboardCard
            to="/tracking/exceptions"
            title="例外対応"
            description="輸送中の例外イベントを登録・対応状況を確認"
            icon="⚠️"
            color="bg-yellow-50"
          />
        </Section>
      )}

      {isHandlerOrAdmin && (
        <Section title="荷役">
          <DashboardCard
            to="/handling"
            title="荷役作業"
            description="積み込み・陸揚げ・通関などの荷役実績を登録"
            icon="🏗️"
            color="bg-amber-50"
          />
        </Section>
      )}

      {isAccountantOrAdmin && (
        <Section title="精算・請求">
          <DashboardCard
            to="/billing"
            title="請求一覧"
            description="料金算出・精算書発行・入金確認"
            icon="💰"
            color="bg-emerald-50"
          />
          <DashboardCard
            to="/billing/overdue"
            title="督促一覧"
            description="支払期限超過の請求を確認・対応"
            icon="🔔"
            color="bg-red-50"
          />
        </Section>
      )}
    </div>
  );
}
