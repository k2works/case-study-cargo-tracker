import { HandlingActivityList } from '../features/handling/components/HandlingActivityList'

export function HandlingActivityListPage() {
  return (
    <div className="max-w-5xl mx-auto">
      <h1 className="text-2xl font-bold mb-4">荷役作業履歴</h1>
      <HandlingActivityList />
    </div>
  )
}
