import type { ReactElement } from 'react';
import type { RouteCandidate } from '../../contexts/routing/domain/model/route-candidate-finder.js';

export function RouteCandidateTable({ candidates }: { candidates: RouteCandidate[] }): ReactElement {
  return (
    <table className="table" data-testid="route-candidate-list">
      <thead>
        <tr>
          <th>航海番号</th>
          <th>所要日数</th>
          <th>経由港</th>
          <th>費用</th>
        </tr>
      </thead>
      <tbody>
        {candidates.length === 0 ? (
          <tr>
            <td colSpan={4} className="text-muted" data-testid="route-candidate-empty">
              期限内に到達可能な経路候補がありません。条件を調整してください。
            </td>
          </tr>
        ) : (
          candidates.map((candidate) => (
            <tr key={candidate.voyageNumbers.join('-')}>
              <td>{candidate.voyageNumbers.join(' / ')}</td>
              <td>{candidate.transitDays} 日</td>
              <td>{candidate.transitPorts.length > 0 ? candidate.transitPorts.join('、') : '直行'}</td>
              <td>{candidate.estimatedCost.toLocaleString('ja-JP')} 円</td>
            </tr>
          ))
        )}
      </tbody>
    </table>
  );
}
