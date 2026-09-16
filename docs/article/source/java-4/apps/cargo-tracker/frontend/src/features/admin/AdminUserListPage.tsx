import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ALERT,
  BUTTON_PRIMARY,
  CARD,
  NOTICE,
  PAGE_TITLE,
  TABLE,
  TABLE_CAPTION,
  TD,
  TH,
} from '@/shared/ui/styles';
import { roleLabel } from '@/shared/auth/roles';
import { fetchAdminUsers, unlockUser, type AdminUserView } from './api';

/**
 * S90 利用者管理（US31）。
 *
 * <p>断った理由（資格情報の誤り・ロック中・無効）はこの画面にも出さない。
 * 出すと、管理者の端末を覗いた第三者に「その利用者名は実在する」と伝わる。
 * 理由は auth_audit_log に残し、必要なときに問い合わせる。</p>
 */
export function AdminUserListPage() {
  const client = useQueryClient();
  const { data, isPending, isError } = useQuery({
    queryKey: ['admin-users'],
    queryFn: fetchAdminUsers,
  });

  const unlock = useMutation({
    mutationFn: unlockUser,
    onSuccess: () => client.invalidateQueries({ queryKey: ['admin-users'] }),
  });

  return (
    <section>
      <h1 className={PAGE_TITLE}>利用者管理</h1>

      {isPending && <output className={`${NOTICE} mt-4`}>読み込み中…</output>}
      {isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          利用者一覧を取得できませんでした
        </p>
      )}
      {unlock.isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          ロックを解除できませんでした
        </p>
      )}

      {data?.state === 'ready' && (
        <div className={`${CARD} mt-4 overflow-x-auto`}>
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>利用者の一覧と状態</caption>
            <thead>
              <tr>
                <th scope="col" className={TH}>利用者名</th>
                <th scope="col" className={TH}>表示名</th>
                <th scope="col" className={TH}>担当</th>
                <th scope="col" className={TH}>状態</th>
                <th scope="col" className={TH}>操作</th>
              </tr>
            </thead>
            <tbody>
              {data.value.users.map((user) => (
                <tr key={user.username}>
                  <td className={TD}>{user.username}</td>
                  <td className={TD}>{user.displayName}</td>
                  <td className={TD}>{user.roles.map(roleLabel).join('・')}</td>
                  <td className={TD}>
                    <StatusCell user={user} />
                  </td>
                  <td className={TD}>
                    {/* 解除はロック中の行にだけ出す。押しても何も変わらない
                        ボタンを出すと、押した人が状態を読み違える。 */}
                    {user.locked && (
                      <button
                        type="button"
                        className={BUTTON_PRIMARY}
                        disabled={unlock.isPending}
                        onClick={() => unlock.mutate(user.username)}
                      >
                        解除する
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="mt-4 text-sm text-gray-500">
            断った理由（資格情報の誤り・ロック中・無効）は記録にだけ残します。画面と応答では区別しません
          </p>
        </div>
      )}
    </section>
  );
}

function StatusCell({ user }: { readonly user: AdminUserView }) {
  if (!user.enabled) {
    return <span className="text-gray-500">無効</span>;
  }
  if (user.locked && user.lockedUntil) {
    // 「ロック中」だけだと、待てば入れるのか解除が要るのかが分からない。
    return (
      <span className="text-red-700">ロック中（あと {remainingMinutes(user.lockedUntil)} 分）</span>
    );
  }
  if (user.failedAttempts > 0) {
    // ロックに至る前に気づけると、ロックしてから問い合わせを受ける流れを避けられる。
    return <span>失敗 {user.failedAttempts} 回</span>;
  }
  return <span>利用できる</span>;
}

/** 残り分数。切り上げると「あと 0 分」が出ない。 */
function remainingMinutes(lockedUntil: string): number {
  const millis = new Date(lockedUntil).getTime() - Date.now();
  return Math.max(0, Math.ceil(millis / 60_000));
}
