import { Link } from 'react-router'
import { HandlingActivityList } from '../features/handling/components/HandlingActivityList'

export function HandlingActivityListPage() {
  return (
    <div className="p-6">
      <div className="mb-6 flex justify-between items-center">
        <h1 className="text-2xl font-bold text-gray-900">荷役作業履歴</h1>
        <Link
          to="/handling/new"
          className="bg-blue-600 text-white py-2 px-4 rounded-md text-sm hover:bg-blue-700"
          data-testid="handling-new-button"
        >
          新規登録
        </Link>
      </div>
      <HandlingActivityList />
    </div>
  )
}
