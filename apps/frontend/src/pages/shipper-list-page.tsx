import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { searchShippers } from '../features/booking/api'
import { SHIPPER_TYPE_LABELS } from '../features/booking/types'

export function ShipperListPage() {
  // 絞り込み条件を URL に持つ。重複確認から「既存の荷主を使う」で来たとき、
  // その荷主に絞られた状態で開けるようにするため
  const [searchParams, setSearchParams] = useSearchParams()
  const keyword = searchParams.get('keyword') ?? ''
  const [input, setInput] = useState(keyword)

  const { data: shippers = [], isPending } = useQuery({
    queryKey: ['shippers', keyword],
    queryFn: () => searchShippers(keyword),
  })

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">荷主一覧</h1>
        <Link
          to="/booking/shippers/new"
          className="rounded bg-blue-600 px-4 py-2 text-sm text-white"
        >
          荷主を登録する
        </Link>
      </div>

      <form
        className="flex gap-2"
        onSubmit={(event) => {
          event.preventDefault()
          setSearchParams(input.trim() === '' ? {} : { keyword: input.trim() })
        }}
      >
        <label htmlFor="keyword" className="sr-only">
          荷主を探す
        </label>
        <input
          id="keyword"
          type="search"
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder="氏名/社名・メールアドレス"
          className="w-80 rounded border border-gray-300 px-3 py-2"
        />
        <button type="submit" className="rounded border border-gray-300 px-4 py-2">
          検索
        </button>
      </form>

      {isPending && <p className="text-gray-600">読み込んでいます…</p>}

      {!isPending && (
        <p className="text-sm text-gray-700">
          {shippers.length} 件
          {keyword !== '' && <span className="ml-2 text-gray-600">（絞り込み: {keyword}）</span>}
        </p>
      )}

      {!isPending && shippers.length === 0 && (
        <p className="text-gray-600">
          条件に合う荷主が見つかりません。別のキーワードで探すか、新しく登録してください。
        </p>
      )}

      {shippers.length > 0 && (
        <div className="overflow-x-auto">
          <table className="min-w-full border bg-white text-sm">
            <thead className="bg-gray-50 text-left">
              <tr>
                <th className="border-b px-4 py-2">荷主コード</th>
                <th className="border-b px-4 py-2">種別</th>
                <th className="border-b px-4 py-2">氏名/社名</th>
                <th className="border-b px-4 py-2">メールアドレス</th>
                <th className="border-b px-4 py-2">住所</th>
                <th className="border-b px-4 py-2">連絡先</th>
              </tr>
            </thead>
            <tbody>
              {shippers.map((shipper) => (
                <tr key={shipper.id}>
                  <td className="border-b px-4 py-2">{shipper.shipperCode}</td>
                  <td className="border-b px-4 py-2">{SHIPPER_TYPE_LABELS[shipper.type]}</td>
                  <td className="border-b px-4 py-2">{shipper.name}</td>
                  <td className="border-b px-4 py-2">{shipper.email}</td>
                  <td className="border-b px-4 py-2">{shipper.address}</td>
                  <td className="border-b px-4 py-2">{shipper.phone ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
