import { Link } from 'react-router'
import { VoyageList } from '../features/routing/voyage/components/VoyageList'

export function VoyageListPage() {
  return (
    <div className="p-6">
      <div className="mb-6 flex justify-between items-center">
        <h1 className="text-2xl font-bold text-gray-900">航海スケジュール一覧</h1>
        <Link
          to="/routing/voyages/new"
          className="bg-blue-600 text-white py-2 px-4 rounded-md text-sm hover:bg-blue-700"
        >
          新規登録
        </Link>
      </div>
      <VoyageList />
    </div>
  )
}
