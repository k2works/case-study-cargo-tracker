import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';

interface PlaceholderProps {
  user: AuthenticatedUser;
  title: string;
  activePath: string;
  storyNote: string;
}

/**
 * 未実装画面のプレースホルダ。
 * ウォーキングスケルトンで全ルートの到達性・ロール制御を成立させるために用いる。
 */
export function Placeholder({ user, title, activePath, storyNote }: PlaceholderProps): ReactElement {
  return (
    <Layout title={title} user={user} activePath={activePath}>
      <h1 className="h3 mb-3" data-testid="placeholder-heading">
        {title}
      </h1>
      <p className="text-muted" data-testid="placeholder-note">
        {storyNote}（後続イテレーションで実装予定）
      </p>
    </Layout>
  );
}
