import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it } from 'vitest';
import { AppRoutes } from './routes';
import { useAuthStore } from '@/shared/auth/authStore';
import { ROLES, type Role } from '@/shared/auth/roles';
import { NAVIGATION, navigationFor } from '@/shared/ui/navigation';

function loginAs(roles: readonly Role[]) {
  useAuthStore.setState({ user: { username: 'tester', roles, token: 't' } });
}

function renderAt(path: string) {
  // 画面が問い合わせを始めたので、本番と同じ器で描く。
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  sessionStorage.clear();
  useAuthStore.setState({ user: null });
});

describe('ロール別の到達性', () => {
  // ナビに出る画面は必ず開ける。出さない画面は 403 にする。
  // 片方だけ確かめると「ナビには出るのに開くと 403」に気づけない。
  it.each(ROLES)('%s: ナビに出る画面はすべて開ける', (role) => {
    for (const item of navigationFor([role])) {
      loginAs([role]);
      const { unmount } = renderAt(item.path);
      expect(
        screen.queryByText('この画面を開く権限がありません'),
        `${role} は ${item.path}（${item.label}）がナビに出るのに開けない`,
      ).not.toBeInTheDocument();
      unmount();
    }
  });

  /**
   * ナビに出さないが開ける画面。**理由を書いたものだけ**を並べる。
   *
   * 経理の例外一覧は「毎日の入口」ではないのでナビには出さないが、請求詳細の
   * 調整行が根拠の例外を指すので、**指された先は開けなければならない**
   * （開けない場所へ誘わない。IT13 のレビュー 高）。
   */
  const OPEN_WITHOUT_NAV: ReadonlyArray<readonly [Role, string]> = [
    ['ROLE_ACCOUNTANT', '/tracking/exceptions'],
  ];

  it.each(ROLES)('%s: ナビに出ない画面は 403 になる（理由を書いたものを除く）', (role) => {
    const allowedPaths = navigationFor([role]).map((i) => i.path);
    const openWithoutNav = OPEN_WITHOUT_NAV
      .filter(([r]) => r === role).map(([, path]) => path);
    for (const item of NAVIGATION.filter(
      (i) => !allowedPaths.includes(i.path) && !openWithoutNav.includes(i.path))) {
      loginAs([role]);
      const { unmount } = renderAt(item.path);
      expect(
        screen.getByText('この画面を開く権限がありません'),
        `${role} は ${item.path} がナビに出ないのに開けてしまう`,
      ).toBeInTheDocument();
      unmount();
    }
  });

  it('経理は請求の根拠（例外）を開ける（ナビには出さないが、指された先は開く）', () => {
    loginAs(['ROLE_ACCOUNTANT']);
    renderAt('/tracking/exceptions');

    expect(screen.queryByText('この画面を開く権限がありません')).not.toBeInTheDocument();
  });

  it('未認証はログイン画面へ送られる（403 ではない）', () => {
    renderAt('/shippers');

    expect(screen.getByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
  });

  it('ナビの全項目にルートが対応している', () => {
    loginAs(['ROLE_ADMIN', 'ROLE_SALES', 'ROLE_ACCOUNTANT', 'ROLE_TRACKER']);

    for (const item of NAVIGATION) {
      const { unmount } = renderAt(item.path);
      // ルートが無ければ "*" が拾ってダッシュボードに飛ぶ。飛んだら対応漏れ。
      expect(document.body.textContent, `${item.path} にルートが無い`).not.toBe('');
      unmount();
    }
  });
});

describe('ダッシュボードの「今日の作業」', () => {
  it('自分のロールで開ける画面への導線が出る', () => {
    loginAs(['ROLE_SALES']);
    renderAt('/');

    // サイドナビにも同じリンクがあるので、本文（main）に絞って確かめる。
    // ダッシュボードの「今日の作業」から行けることが見たいこと。
    const main = screen.getByRole('main');
    expect(within(main).getByRole('link', { name: '荷主一覧' })).toBeInTheDocument();
    expect(within(main).getByRole('link', { name: '要確認一覧' })).toBeInTheDocument();
  });

  it('開けない画面への導線は出さない', () => {
    loginAs(['ROLE_HANDLER']);
    renderAt('/');

    const main = screen.getByRole('main');
    expect(within(main).queryByRole('link', { name: '荷主一覧' })).not.toBeInTheDocument();
  });
});

describe('403 の見え方', () => {
  it('認証済みの利用者はサイドナビを失わない', () => {
    // 権限の無い画面を開いただけで、その利用者が本来行ける画面への導線まで
    // 消えると、戻る手段が本文のリンク 1 本になる（IT1 レビュー M2）。
    loginAs(['ROLE_SALES']);
    renderAt('/admin/users');

    expect(screen.getByText('この画面を開く権限がありません')).toBeInTheDocument();
    expect(
      screen.getByRole('navigation'),
      '403 でもサイドナビは残る',
    ).toBeInTheDocument();
    expect(within(screen.getByRole('navigation')).getByText('荷主一覧')).toBeInTheDocument();
  });
});

describe('一覧から開く画面（ナビに載せない）', () => {
  // ナビに載せない画面は、上のロール別到達性の検査から外れる。**外れた画面ほど
  // 権限の設定を間違えても気づけない**ので、ここで明示的に確かめる。
  //
  // **経理も参照だけ開く**（IT13）。要確認一覧（S70）が「算出できなかった予約」を
  // 経理宛に出すので、開けないと**気づいた先が行き止まり**になる。正典の画面遷移
  // （ui_design.md）も S70 → S22 を経理の導線として書いている。一覧（/bookings）の
  // ナビには出さない——経理の仕事は予約を探すことではなく、指された予約を見ること。
  const DETAIL_PATH = '/bookings/b-1';
  const ALLOWED: readonly Role[] = [
    'ROLE_SALES', 'ROLE_ROUTING', 'ROLE_TRACKER', 'ROLE_ACCOUNTANT'];

  it.each(ALLOWED)('%s: 予約詳細を開ける', (role) => {
    loginAs([role]);
    renderAt(DETAIL_PATH);

    expect(screen.queryByText('この画面を開く権限がありません')).not.toBeInTheDocument();
  });

  it.each(ROLES.filter((r) => !ALLOWED.includes(r)))('%s: 予約詳細は 403 になる', (role) => {
    loginAs([role]);
    renderAt(DETAIL_PATH);

    expect(screen.getByText('この画面を開く権限がありません')).toBeInTheDocument();
  });

  it('予約一覧を開けるロールは、その詳細も開ける', () => {
    // 一覧と詳細で許可がずれると「一覧には出るのに開くと 403」になる。
    // **詳細のほうが広いのは許す**——経理は一覧から探さず、要確認一覧や請求書から
    // 指された予約だけを開く（IT13）。逆（一覧に出るのに詳細が 403）は許さない。
    const listAllow = NAVIGATION.find((i) => i.path === '/bookings')?.allow ?? [];

    expect(listAllow.filter((role) => !ALLOWED.includes(role))).toEqual([]);
  });
});

describe('画面の中のリンク先が、そのロールで開ける（Try T4）', () => {
  // **画面の中のリンクはナビの検査から外れる。** 外れたリンクほど、許可の
  // 設定を間違えても気づけない——押した人にだけ 403 が出る（IT7 の教訓
  // 「共有画面のリンクもロールで出し分ける」と同じ形）。
  //
  // ここに並べるのは**その画面が実際に出すリンク先**と、**押す人のロール**。
  // 画面側の検査（href がこの形であること）と対で効く。
  const LINKS: readonly { from: string; to: string; role: Role }[] = [
    // S13 見積詳細 → S21 予約登録（この見積で予約する）
    { from: 'S13', to: '/bookings/new?quotationId=Q-1', role: 'ROLE_SALES' },
    // S41 追跡詳細 → S62 自社請求書（荷主は請求書番号を知らない）
    { from: 'S41', to: '/shipper/invoices/by-booking/b-1', role: 'ROLE_SHIPPER' },
    // S62 → S46 自社予約の進み具合（開いた先から戻れるようにする）
    { from: 'S62', to: '/shipper/bookings/b-1', role: 'ROLE_SHIPPER' },
    // S61 請求詳細 → S22 予約詳細（金額の相手を開く）
    { from: 'S61', to: '/bookings/b-1', role: 'ROLE_ACCOUNTANT' },
    // ダッシュボード → 未払いだけの請求一覧（督促の起点）
    { from: 'S02', to: '/invoices?overdue=true', role: 'ROLE_ACCOUNTANT' },
  ];

  it.each(LINKS)('$from のリンク先 $to は $role で開ける', ({ to, role }) => {
    loginAs([role]);
    renderAt(to);

    expect(screen.queryByText('この画面を開く権限がありません')).not.toBeInTheDocument();
  });
});

