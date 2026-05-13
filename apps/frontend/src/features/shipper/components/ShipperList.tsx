import { useShippers } from '../hooks/useShippers'

export function ShipperList() {
  const { data: shippers, isLoading, isError } = useShippers()

  if (isLoading) return <p className="text-gray-500">読み込み中...</p>
  if (isError) return <p className="text-red-600">データの取得に失敗しました。</p>

  if (!shippers || shippers.length === 0) {
    return <p className="text-gray-500">荷主が登録されていません。</p>
  }

  return (
    <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
      <table className="w-full text-sm">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-left text-gray-700">荷主コード</th>
            <th className="px-4 py-3 text-left text-gray-700">氏名/社名</th>
            <th className="px-4 py-3 text-left text-gray-700">種別</th>
            <th className="px-4 py-3 text-left text-gray-700">メール</th>
            <th className="px-4 py-3 text-left text-gray-700">電話</th>
          </tr>
        </thead>
        <tbody>
          {shippers.map((shipper) => (
            <tr key={shipper.id} className="border-t border-gray-200 hover:bg-gray-50">
              <td className="px-4 py-3 font-mono">{shipper.shipperCode}</td>
              <td className="px-4 py-3">{shipper.name}</td>
              <td className="px-4 py-3">
                <span className={`inline-block px-2 py-0.5 rounded text-xs ${
                  shipper.shipperType === 'CORPORATE'
                    ? 'bg-blue-100 text-blue-800'
                    : 'bg-gray-100 text-gray-800'
                }`}>
                  {shipper.shipperType === 'CORPORATE' ? '法人' : '個人'}
                </span>
              </td>
              <td className="px-4 py-3">{shipper.email}</td>
              <td className="px-4 py-3">{shipper.phone ?? '\u2014'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
