import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';

interface HandlingNewProps {
  user: AuthenticatedUser;
  csrfToken?: string;
  error?: string;
  values?: Record<string, string>;
}

/**
 * 荷役作業登録画面（/handling/new・US15/US16）。
 * 追跡番号で貨物を特定し、作業種別・日時・場所（UN/LOCODE）を登録する。
 * 「引取」選択時のみ荷受人確認フィールドを表示する（最小 JS・htmx 不使用のため常設 + 表示制御）。
 */
export function HandlingNew({ user, csrfToken, error, values = {} }: HandlingNewProps): ReactElement {
  return (
    <Layout title="荷役作業登録" user={user} activePath="/handling" csrfToken={csrfToken}>
      <h1 className="h3 mb-3" data-testid="handling-new-heading">荷役作業登録</h1>
      {error && (
        <div className="alert alert-danger" role="alert" data-testid="handling-error">
          {error}
        </div>
      )}
      <form action="/handling" method="post" className="col-md-6" data-testid="handling-form">
        {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}
        <div className="mb-3">
          <label className="form-label" htmlFor="trackingNumber">追跡番号</label>
          <input
            type="text"
            className="form-control"
            id="trackingNumber"
            name="trackingNumber"
            placeholder="TRK-XXXXXXXXXXXX"
            defaultValue={values.trackingNumber ?? ''}
            required
          />
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="eventType">作業種別</label>
          <select className="form-select" id="eventType" name="eventType" defaultValue={values.eventType ?? 'RECEIVE'}>
            <option value="RECEIVE">受領</option>
            <option value="LOAD">積込</option>
            <option value="UNLOAD">荷降し</option>
            <option value="CLAIM">引取</option>
          </select>
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="completionTime">作業日時</label>
          <input
            type="datetime-local"
            className="form-control"
            id="completionTime"
            name="completionTime"
            defaultValue={values.completionTime ?? ''}
            required
          />
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="location">作業場所（UN/LOCODE）</label>
          <input
            type="text"
            className="form-control"
            id="location"
            name="location"
            placeholder="JPTYO"
            defaultValue={values.location ?? ''}
            required
          />
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="voyageNumber">航海番号（積込・荷降し時は必須）</label>
          <input
            type="text"
            className="form-control"
            id="voyageNumber"
            name="voyageNumber"
            defaultValue={values.voyageNumber ?? ''}
          />
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="consigneeConfirmation">荷受人確認（引取時のみ・署名または確認コード）</label>
          <input
            type="text"
            className="form-control"
            id="consigneeConfirmation"
            name="consigneeConfirmation"
            defaultValue={values.consigneeConfirmation ?? ''}
            data-testid="consignee-confirmation"
          />
        </div>
        <button type="submit" className="btn btn-primary" data-testid="handling-submit">登録</button>
        <a href="/handling" className="btn btn-outline-secondary ms-2">一覧に戻る</a>
      </form>
    </Layout>
  );
}
