import { VoyageForm } from '../features/routing/voyage/components/VoyageForm'

export function VoyageNewPage() {
  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">航海スケジュールの登録</h1>
      </div>
      <div className="bg-white border border-gray-200 rounded-lg p-6 max-w-3xl">
        <VoyageForm />
      </div>
    </div>
  )
}
