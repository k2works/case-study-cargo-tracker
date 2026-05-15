import { Link } from 'react-router'
import { QuotationList } from '../features/booking/quotation/components/QuotationList'

export function QuotationListPage() {
  return (
    <div className="p-6">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">見積一覧</h1>
        <Link
          to="/quotations/new"
          className="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700"
        >
          + 新規見積
        </Link>
      </div>
      <QuotationList />
    </div>
  )
}
