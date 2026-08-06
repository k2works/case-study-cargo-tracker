import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import type { CustomsDeclarationSummary } from '../../contexts/handling/application/queryservices/customs-query.service.js';
import { CUSTOMS_STATUS_LABELS } from '../../contexts/handling/domain/model/customs-status.js';

interface CustomsProps {
  user: AuthenticatedUser;
  trackingNumber: string;
  declarations: CustomsDeclarationSummary[];
  /** 紐付け可能な最新荷役作業があり申告フォームを表示するか（false のとき登録フォーム非表示） */
  canRegister: boolean;
  csrfToken?: string;
  success?: string;
  warning?: string;
  error?: string;
}

function statusLabel(status: string): string {
  return (CUSTOMS_STATUS_LABELS as Record<string, string>)[status] ?? status;
}

function formatDateTime(value: Date | null): string {
  return value === null ? '—' : value.toISOString().replace('T', ' ').slice(0, 16);
}

/** 通関状態バッジ（審査中 / 通関済 / 留置中 / 不可） */
function StatusBadge({ status }: { status: string }): ReactElement {
  const className =
    status === 'CLEARED'
      ? 'bg-success'
      : status === 'HELD'
        ? 'bg-warning text-dark'
        : status === 'REJECTED'
          ? 'bg-danger'
          : 'bg-secondary';
  return (
    <span className={`badge ${className}`} data-testid={`customs-badge-${status}`}>
      {statusLabel(status)}
    </span>
  );
}

/**
 * 通関ステータス画面（/tracking/{trackingNumber}/customs・US16 前提）。
 * 対象貨物の通関申告一覧と、申告登録・状態更新（PRG）を提供する。対象ロール: 追跡管理者・荷役作業員。
 * HELD は CUSTOMS_HOLD 例外の自動登録契機となる旨を明示する。
 */
export function Customs({
  user,
  trackingNumber,
  declarations,
  canRegister,
  csrfToken,
  success,
  warning,
  error,
}: CustomsProps): ReactElement {
  const base = `/tracking/${trackingNumber}/customs`;
  return (
    <Layout title="通関ステータス" user={user} activePath="/handling" csrfToken={csrfToken}>
      {success && (
        <div className="alert alert-success" role="alert" data-testid="customs-success">
          {success}
        </div>
      )}
      {warning && (
        <div className="alert alert-warning" role="alert" data-testid="customs-warning">
          {warning}
        </div>
      )}
      {error && (
        <div className="alert alert-danger" role="alert" data-testid="customs-error">
          {error}
        </div>
      )}
      <h1 className="h3 mb-3" data-testid="customs-heading">通関ステータス</h1>
      <p className="text-muted">追跡番号: {trackingNumber}</p>

      {declarations.length === 0 ? (
        <p className="text-muted" data-testid="no-declarations">申告なし</p>
      ) : (
        <table className="table" data-testid="customs-list">
          <thead>
            <tr>
              <th>申告番号</th>
              <th>状態</th>
              <th>申告日時</th>
              <th>通関完了日時</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {declarations.map((declaration) => {
              const terminal = declaration.status === 'CLEARED' || declaration.status === 'REJECTED';
              return (
                <tr key={declaration.declarationNumber} data-testid={`customs-row-${declaration.declarationNumber}`}>
                  <td>{declaration.declarationNumber}</td>
                  <td>
                    <StatusBadge status={declaration.status} />
                    {declaration.status === 'HELD' && (
                      <div className="small text-danger mt-1" data-testid="customs-hold-note">
                        留置中: CUSTOMS_HOLD 例外が登録されます。荷主へ書類再提出を依頼し、提出確認後に通関済へ更新してください。
                      </div>
                    )}
                  </td>
                  <td>{formatDateTime(declaration.declaredAt)}</td>
                  <td>{formatDateTime(declaration.clearedAt)}</td>
                  <td>
                    {terminal ? (
                      <span className="text-muted small">確定済</span>
                    ) : (
                      <form action={`${base}/${declaration.declarationNumber}/status`} method="post" className="d-flex gap-1">
                        {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}
                        <button type="submit" name="status" value="CLEARED" className="btn btn-sm btn-success" data-testid="customs-clear">
                          通関済
                        </button>
                        {/* 留置は審査中（PENDING）からのみ許可。HELD → HELD の無意味な遷移は出さない */}
                        {declaration.status === 'PENDING' && (
                          <button type="submit" name="status" value="HELD" className="btn btn-sm btn-warning" data-testid="customs-hold">
                            留置
                          </button>
                        )}
                        <button type="submit" name="status" value="REJECTED" className="btn btn-sm btn-danger" data-testid="customs-reject">
                          不可
                        </button>
                      </form>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      {canRegister && (
        <section className="mt-4">
          <h2 className="h5">通関申告の登録</h2>
          <p className="text-muted small">対象貨物の最新の荷役作業に紐付けて通関申告（審査中）を登録します。</p>
          <form action={base} method="post" className="col-md-6" data-testid="customs-form">
            {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}
            <div className="mb-3">
              <label className="form-label" htmlFor="remarks">備考（任意）</label>
              <input type="text" className="form-control" id="remarks" name="remarks" data-testid="customs-remarks" />
            </div>
            <button type="submit" className="btn btn-primary" data-testid="customs-register">申告登録</button>
          </form>
        </section>
      )}

      <a href={`/tracking/${trackingNumber}`} className="btn btn-outline-secondary mt-3" data-testid="customs-back">
        追跡詳細に戻る
      </a>
    </Layout>
  );
}
