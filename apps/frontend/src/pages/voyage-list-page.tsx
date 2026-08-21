import { useState } from 'react'
import { Link } from 'react-router-dom'
import { businessToday, formatBusinessDateTime } from '../lib/business-time'
import { useVoyageLocations, useVoyages } from '../features/routing/queries'
import {
  ROUTING_CARGO_TYPE_LABELS,
  type RoutingCargoType,
  type VoyageSearchCriteria,
} from '../features/routing/types'

const EMPTY_CRITERIA: VoyageSearchCriteria = {
  origin: '',
  destination: '',
  departureFrom: '',
  departureTo: '',
  cargoType: '',
}

/**
 * 既定は本日以降の出発だけを見せる。
 *
 * 並びは出発日の昇順なので、絞らないと過去の便が先頭を占める。朝いちばんに開いて
 * 最初に目に入るのがもう出てしまった船では、一覧そのものが信用されなくなる。
 */
function defaultCriteria(): VoyageSearchCriteria {
  return { ...EMPTY_CRITERIA, departureFrom: businessToday() }
}

/**
 * 航海スケジュールの一覧・検索（US07）。
 *
 * 経路設計者は「この予約に合う船はあるか」を探しに来る。条件に合うものが無かったとき、
 * 画面が「0 件です」で終わると、条件のどれが効きすぎたのかが分からない。
 * 何で絞ったかを見せ、条件を緩めて探し直せるようにする。
 */
export function VoyageListPage() {
  const [form, setForm] = useState<VoyageSearchCriteria>(defaultCriteria)
  const [criteria, setCriteria] = useState<VoyageSearchCriteria>(defaultCriteria)
  const [includeDeparted, setIncludeDeparted] = useState(false)
  const { data, isLoading, isError } = useVoyages(criteria)
  const { data: locations = [] } = useVoyageLocations()

  const hasCriteria = Object.values(criteria).some((value) => value !== '')

  function submit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    setCriteria(form)
  }

  function clearCriteria() {
    setForm(EMPTY_CRITERIA)
    setCriteria(EMPTY_CRITERIA)
    setIncludeDeparted(true)
  }

  /** 出港済みを含めるかは、期間の下限そのものを外すことで表す。 */
  function toggleIncludeDeparted(include: boolean) {
    setIncludeDeparted(include)
    const next = { ...form, departureFrom: include ? '' : businessToday() }
    setForm(next)
    setCriteria(next)
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">航海スケジュール</h1>
        <Link
          to="/routing/voyages/new"
          className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
        >
          航海を登録する
        </Link>
      </div>

      <form onSubmit={submit} className="space-y-4 rounded border border-gray-200 bg-white p-4">
        <div className="grid gap-4 md:grid-cols-3">
          <div>
            <label htmlFor="origin" className="block text-sm font-medium text-gray-700">
              出発地
            </label>
            <select
              id="origin"
              value={form.origin}
              onChange={(event) => setForm({ ...form, origin: event.target.value })}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            >
              <option value="">指定なし</option>
              {locations.map((location) => (
                <option key={location.unLocode} value={location.unLocode}>
                  {location.name}（{location.unLocode}）
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="destination" className="block text-sm font-medium text-gray-700">
              目的地
            </label>
            <select
              id="destination"
              value={form.destination}
              onChange={(event) => setForm({ ...form, destination: event.target.value })}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            >
              <option value="">指定なし</option>
              {locations.map((location) => (
                <option key={location.unLocode} value={location.unLocode}>
                  {location.name}（{location.unLocode}）
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="cargoType" className="block text-sm font-medium text-gray-700">
              積みたい貨物
            </label>
            <select
              id="cargoType"
              value={form.cargoType}
              onChange={(event) =>
                setForm({
                  ...form,
                  cargoType: event.target.value as RoutingCargoType | '',
                })
              }
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            >
              <option value="">指定なし</option>
              {(Object.keys(ROUTING_CARGO_TYPE_LABELS) as RoutingCargoType[]).map((cargoType) => (
                <option key={cargoType} value={cargoType}>
                  {ROUTING_CARGO_TYPE_LABELS[cargoType]}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="departureFrom" className="block text-sm font-medium text-gray-700">
              出発日（この日以降）
            </label>
            <input
              id="departureFrom"
              type="date"
              value={form.departureFrom}
              onChange={(event) => setForm({ ...form, departureFrom: event.target.value })}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            />
          </div>

          <div>
            <label htmlFor="departureTo" className="block text-sm font-medium text-gray-700">
              出発日（この日まで）
            </label>
            <input
              id="departureTo"
              type="date"
              value={form.departureTo}
              onChange={(event) => setForm({ ...form, departureTo: event.target.value })}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            />
          </div>
        </div>

        <div className="flex items-center gap-2">
          <input
            id="includeDeparted"
            type="checkbox"
            checked={includeDeparted}
            onChange={(event) => toggleIncludeDeparted(event.target.checked)}
            className="h-4 w-4 rounded border-gray-300"
          />
          <label htmlFor="includeDeparted" className="text-sm text-gray-700">
            出港済みも含める
          </label>
        </div>

        <div className="flex gap-2">
          <button
            type="submit"
            className="rounded bg-gray-800 px-4 py-2 text-white hover:bg-gray-900"
          >
            検索する
          </button>
          <button
            type="button"
            onClick={clearCriteria}
            className="rounded border border-gray-300 px-4 py-2 text-gray-700 hover:bg-gray-50"
          >
            条件をすべて外す
          </button>
        </div>
      </form>

      {isLoading && <p className="text-gray-600">読み込んでいます…</p>}
      {isError && (
        <p className="rounded border border-red-200 bg-red-50 p-3 text-red-700">
          航海スケジュールを読み込めませんでした。時間をおいて再度お試しください。
        </p>
      )}

      {data?.truncated === true && (
        <p className="rounded border border-yellow-200 bg-yellow-50 p-3 text-yellow-800">
          条件に合う航海は {data.totalCount} 件ありますが、先頭の {data.limit}{' '}
          件だけを表示しています。 条件を絞り込んでください。
        </p>
      )}

      {data?.voyages.length === 0 && (
        <div className="rounded border border-gray-200 bg-gray-50 p-4 text-gray-700">
          <p className="font-medium">条件に合う航海はありませんでした。</p>
          {hasCriteria ? (
            <>
              <p className="mt-1 text-sm">
                条件を緩めると見つかることがあります。とくに出発日の範囲と積みたい貨物は、
                絞り込みが強く効きます。
              </p>
              <button
                type="button"
                onClick={clearCriteria}
                className="mt-3 rounded border border-gray-300 bg-white px-4 py-2 text-gray-700 hover:bg-gray-100"
              >
                条件をすべて外して探し直す
              </button>
            </>
          ) : (
            <p className="mt-1 text-sm">
              まだ航海が 1 件も登録されていません。「航海を登録する」から追加してください。
            </p>
          )}
        </div>
      )}

      {data !== undefined && data.voyages.length > 0 && (
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-gray-300 bg-gray-50 text-left">
              <th className="px-3 py-2">航海番号</th>
              <th className="px-3 py-2">船名</th>
              <th className="px-3 py-2">運送会社</th>
              <th className="px-3 py-2">出発地</th>
              <th className="px-3 py-2">目的地</th>
              <th className="px-3 py-2">出発日時</th>
              <th className="px-3 py-2">到着日時</th>
              <th className="px-3 py-2">運べる貨物</th>
              <th className="px-3 py-2">操作</th>
            </tr>
          </thead>
          <tbody>
            {data.voyages.map((voyage) => (
              <tr key={voyage.voyageNumber} className="border-b border-gray-200">
                {/* 一覧は出発地と目的地しか見せない。途中の寄港地と区間ごとの時刻は
                    詳細で確かめる（#552）。候補に出た航海が使えるかの判断に要る */}
                <td className="px-3 py-2 font-mono">
                  <Link
                    to={`/routing/voyages/${encodeURIComponent(voyage.voyageNumber)}`}
                    className="text-blue-600 hover:underline"
                  >
                    {voyage.voyageNumber}
                  </Link>
                </td>
                <td className="px-3 py-2">{voyage.vesselName}</td>
                <td className="px-3 py-2">{voyage.carrierName}</td>
                <td className="px-3 py-2">
                  {voyage.originName}（{voyage.originUnLocode}）
                </td>
                <td className="px-3 py-2">
                  {voyage.destinationName}（{voyage.destinationUnLocode}）
                </td>
                <td className="px-3 py-2">{formatBusinessDateTime(voyage.departureTime)}</td>
                <td className="px-3 py-2">{formatBusinessDateTime(voyage.arrivalTime)}</td>
                <td className="px-3 py-2">
                  {voyage.supportedCargoTypes
                    .map((cargoType) => ROUTING_CARGO_TYPE_LABELS[cargoType])
                    .join('、')}
                </td>
                <td className="px-3 py-2">
                  {/* 航海番号を引き継いで登録画面へ。番号を打ち直させると打ち間違いで
                      別の航海ができる */}
                  <Link
                    to={`/routing/voyages/new?voyageNumber=${encodeURIComponent(voyage.voyageNumber)}`}
                    className="text-blue-600 hover:underline"
                  >
                    更新する
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
