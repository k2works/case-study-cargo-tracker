import { QuotationForm } from '../features/booking/quotation/components/QuotationForm'

export function QuotationNewPage() {
  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">輸送見積の作成</h1>
      </div>
      <div className="bg-white border border-gray-200 rounded-lg p-6 max-w-2xl">
        <QuotationForm />
      </div>
    </div>
  )
}
