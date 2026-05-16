import type { CandidateItem } from '../types/workbench'

interface Props {
  candidates: CandidateItem[]
  onSelect: (index: number) => void
  isSubmitting: boolean
}

export function RouteCandidateTable({ candidates, onSelect, isSubmitting }: Props) {
  if (candidates.length === 0) {
    return (
      <div
        data-testid="no-candidates"
        className="rounded border border-yellow-200 bg-yellow-50 px-4 py-3 text-sm text-yellow-800"
      >
        条件に合う経路候補が見つかりませんでした。条件を調整してください。
      </div>
    )
  }

  return (
    <table className="w-full text-sm border-collapse border border-gray-200">
      <thead className="bg-gray-50">
        <tr>
          <th className="border border-gray-200 px-3 py-2 text-left">**航海番号**</th>
          <th className="border border-gray-200 px-3 py-2 text-left">**経由港**</th>
          <th className="border border-gray-200 px-3 py-2 text-right">**所要日数**</th>
          <th className="border border-gray-200 px-3 py-2 text-center">**選択**</th>
        </tr>
      </thead>
      <tbody>
        {candidates.map((c, i) => (
          <tr key={i} className="hover:bg-blue-50">
            <td className="border border-gray-200 px-3 py-2 font-mono">
              {c.voyageNumbers.join(' → ')}
            </td>
            <td className="border border-gray-200 px-3 py-2 font-mono">
              {c.via.length > 0 ? c.via.join(', ') : '直行'}
            </td>
            <td className="border border-gray-200 px-3 py-2 text-right">{c.transitDays} 日</td>
            <td className="border border-gray-200 px-3 py-2 text-center">
              <button
                type="button"
                data-testid={`select-candidate-${i}`}
                onClick={() => onSelect(i)}
                disabled={isSubmitting}
                className="rounded bg-blue-600 px-3 py-1 text-xs text-white hover:bg-blue-700 disabled:opacity-50"
              >
                この経路を選択
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
