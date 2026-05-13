import { ShipperForm } from '../features/shipper/components/ShipperForm'

export function ShipperNewPage() {
  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">荷主の登録</h1>
      </div>
      <div className="bg-white border border-gray-200 rounded-lg p-6 max-w-xl">
        <ShipperForm />
      </div>
    </div>
  )
}
