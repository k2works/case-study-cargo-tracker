import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useRegisterShipper } from '../features/booking/hooks/useShippers'

export function ShipperNewPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState({
    name: '',
    email: '',
    phone: '',
    shipperType: 'INDIVIDUAL' as 'INDIVIDUAL' | 'CORPORATE',
    contractNumber: '',
    discountRate: '',
  })
  const register = useRegisterShipper()

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    register.mutate(
      {
        name: form.name,
        email: form.email,
        phone: form.phone,
        shipperType: form.shipperType,
        ...(form.shipperType === 'CORPORATE' && {
          contractNumber: form.contractNumber,
          discountRate: Number(form.discountRate) / 100,
        }),
      },
      { onSuccess: () => navigate('/shippers') }
    )
  }

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">荷主の登録</h1>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg p-6 max-w-xl">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700">氏名/社名</label>
            <input
              type="text"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
              className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">メールアドレス</label>
            <input
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              required
              className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">電話番号</label>
            <input
              type="tel"
              value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })}
              className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">荷主種別</label>
            <div className="mt-1 flex gap-4">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="radio"
                  value="INDIVIDUAL"
                  checked={form.shipperType === 'INDIVIDUAL'}
                  onChange={() => setForm({ ...form, shipperType: 'INDIVIDUAL' })}
                />
                個人
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="radio"
                  value="CORPORATE"
                  checked={form.shipperType === 'CORPORATE'}
                  onChange={() => setForm({ ...form, shipperType: 'CORPORATE' })}
                />
                法人
              </label>
            </div>
          </div>
          {form.shipperType === 'CORPORATE' && (
            <>
              <div>
                <label className="block text-sm font-medium text-gray-700">契約番号</label>
                <input
                  type="text"
                  value={form.contractNumber}
                  onChange={(e) => setForm({ ...form, contractNumber: e.target.value })}
                  required
                  className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">割引率（0〜30%）</label>
                <input
                  type="number"
                  value={form.discountRate}
                  onChange={(e) => setForm({ ...form, discountRate: e.target.value })}
                  min="0"
                  max="30"
                  step="1"
                  required
                  className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
                />
              </div>
            </>
          )}
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={register.isPending}
              className="flex-1 bg-blue-600 text-white py-2 px-4 rounded-md text-sm hover:bg-blue-700 disabled:opacity-50"
            >
              {register.isPending ? '登録中...' : '登録する'}
            </button>
            <button
              type="button"
              onClick={() => navigate('/shippers')}
              className="flex-1 bg-gray-100 text-gray-700 py-2 px-4 rounded-md text-sm hover:bg-gray-200"
            >
              キャンセル
            </button>
          </div>
          {register.isError && (
            <p className="text-red-600 text-sm">登録に失敗しました。</p>
          )}
        </form>
      </div>
    </div>
  )
}
