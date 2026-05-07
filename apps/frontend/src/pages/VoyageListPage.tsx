import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { VoyageList } from '../features/routing/components/VoyageList'
import type { Voyage } from '../features/routing/types/voyage'

export function VoyageListPage() {
  const navigate = useNavigate()
  const [departureFilter, setDepartureFilter] = useState('')
  const [arrivalFilter, setArrivalFilter] = useState('')

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-bold text-gray-900">航海スケジュール管理</h1>
      </div>

      {/* 検索フォーム */}
      <div className="bg-white border border-gray-200 rounded-lg p-4 mb-4 flex items-center gap-4">
        <label className="text-sm font-medium text-gray-700 whitespace-nowrap">出発港</label>
        <input
          type="text"
          value={departureFilter}
          onChange={(e) => setDepartureFilter(e.target.value)}
          placeholder="JPOSA"
          className="border border-gray-300 rounded-md px-3 py-1.5 text-sm w-32 focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
        <label className="text-sm font-medium text-gray-700 whitespace-nowrap">到着港</label>
        <input
          type="text"
          value={arrivalFilter}
          onChange={(e) => setArrivalFilter(e.target.value)}
          placeholder="USLAX"
          className="border border-gray-300 rounded-md px-3 py-1.5 text-sm w-32 focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
        <button className="rounded-md bg-gray-100 px-4 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-200">
          検索
        </button>
      </div>

      {/* 新規登録ボタン */}
      <div className="mb-4">
        <Link
          to="/voyages/new"
          className="inline-flex items-center gap-1 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          新規登録
        </Link>
      </div>

      {/* テーブル */}
      <VoyageList onEdit={(voyage: Voyage) => navigate(`/voyages/${voyage.voyageNumber}/edit`)} />
    </div>
  )
}
