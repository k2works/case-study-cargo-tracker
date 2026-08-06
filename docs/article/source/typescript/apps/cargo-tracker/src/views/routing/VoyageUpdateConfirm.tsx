import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';

export interface VoyageUpdateConfirmValues {
  voyageNumber: string;
  current: VoyageScheduleValues;
  updated: VoyageScheduleValues;
}

interface VoyageScheduleValues {
  departureLocation: string;
  arrivalLocation: string;
  departureTime: string;
  arrivalTime: string;
  transitLocation?: string;
  transitArrivalTime?: string;
  transitDepartureTime?: string;
}

interface VoyageUpdateConfirmProps {
  user: AuthenticatedUser;
  values: VoyageUpdateConfirmValues;
}

export function VoyageUpdateConfirm({ user, values }: VoyageUpdateConfirmProps): ReactElement {
  const voyageNumber = encodeURIComponent(values.voyageNumber);
  return (
    <Layout title="航海スケジュール更新確認" user={user} activePath="/voyages">
      <h1 className="h3 mb-4" data-testid="voyage-confirm-heading">
        航海スケジュール更新確認
      </h1>
      <p className="text-muted">航海番号 {values.voyageNumber} の既存内容と更新内容を確認してください。</p>
      <div className="table-responsive mb-4">
        <table className="table table-bordered align-middle" data-testid="voyage-update-diff">
          <thead>
            <tr>
              <th scope="col">項目</th>
              <th scope="col">既存内容</th>
              <th scope="col">更新内容</th>
            </tr>
          </thead>
          <tbody>
            <DiffRow label="出発港" before={values.current.departureLocation} after={values.updated.departureLocation} />
            <DiffRow label="到着港" before={values.current.arrivalLocation} after={values.updated.arrivalLocation} />
            <DiffRow label="出発日時" before={values.current.departureTime} after={values.updated.departureTime} />
            <DiffRow label="到着日時" before={values.current.arrivalTime} after={values.updated.arrivalTime} />
            <DiffRow label="寄港地" before={values.current.transitLocation ?? ''} after={values.updated.transitLocation ?? ''} />
            <DiffRow label="寄港到着日時" before={values.current.transitArrivalTime ?? ''} after={values.updated.transitArrivalTime ?? ''} />
            <DiffRow label="寄港出発日時" before={values.current.transitDepartureTime ?? ''} after={values.updated.transitDepartureTime ?? ''} />
          </tbody>
        </table>
      </div>
      <div className="d-flex gap-2">
        <form action={`/voyages/${voyageNumber}`} method="post">
          <HiddenScheduleFields values={values.updated} />
          <button type="submit" className="btn btn-primary" data-testid="voyage-confirm-submit">
            更新する
          </button>
        </form>
        <form action={`/voyages/${voyageNumber}/cancel`} method="post">
          <button type="submit" className="btn btn-outline-secondary" data-testid="voyage-confirm-cancel">
            キャンセル
          </button>
        </form>
      </div>
    </Layout>
  );
}

function DiffRow({ label, before, after }: { label: string; before: string; after: string }): ReactElement {
  const changed = before !== after;
  return (
    <tr className={changed ? 'table-warning' : undefined}>
      <th scope="row">{label}</th>
      <td>{before}</td>
      <td>{after}</td>
    </tr>
  );
}

function HiddenScheduleFields({ values }: { values: VoyageScheduleValues }): ReactElement {
  return (
    <>
      <input type="hidden" name="departureLocation" value={values.departureLocation} />
      <input type="hidden" name="arrivalLocation" value={values.arrivalLocation} />
      <input type="hidden" name="departureTime" value={values.departureTime} />
      <input type="hidden" name="arrivalTime" value={values.arrivalTime} />
      <input type="hidden" name="transitLocation" value={values.transitLocation ?? ''} />
      <input type="hidden" name="transitArrivalTime" value={values.transitArrivalTime ?? ''} />
      <input type="hidden" name="transitDepartureTime" value={values.transitDepartureTime ?? ''} />
    </>
  );
}
