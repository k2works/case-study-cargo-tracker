import type React from 'react'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { businessLocalToInstant, instantToBusinessLocal } from '../lib/business-time'
import { ApiError } from '../lib/api-client'
import {
  useRegisterVoyage,
  useUpdateVoyage,
  useVoyage,
  useVoyageLocations,
} from '../features/routing/queries'
import {
  ROUTING_CARGO_TYPE_LABELS,
  type MovementInput,
  type RoutingCargoType,
  type VoyageDifference,
  type VoyageRequest,
} from '../features/routing/types'
import { voyageInvalidMessage } from '../features/routing/voyage-validation'
import { MovementsFieldset } from '../features/routing/components/movements-fieldset'


let movementKeySequence = 0

function emptyMovement(departureUnLocode = ''): MovementInput {
  movementKeySequence += 1
  return {
    key: `movement-${movementKeySequence}`,
    departureUnLocode,
    arrivalUnLocode: '',
    departureTime: '',
    arrivalTime: '',
  }
}

/** サーバが理由を添えて拒否した（400）ときだけ、その理由を返す。 */
function invalidInputMessage(error: unknown): string | null {
  if (!(error instanceof ApiError) || error.status !== 400) {
    return null
  }
  const body = error.body as { message?: string } | undefined
  return body?.message ?? '入力内容を確認してください。'
}

/**
 * 航海スケジュールの登録・更新（US24・US25）。
 *
 * 同じ航海番号が既にあるときは、拒まずに差分を見せる。経路設計者が同じ番号を入れるのは
 * 多くの場合スケジュールの差し替えであり、そこで止めると別の番号を作る（同じ航海が 2 つに
 * なる）か、一覧から探し直すことになる。
 */
export function VoyageRegisterPage() {
  const [searchParams] = useSearchParams()
  const requestedNumber = searchParams.get('voyageNumber')
  const [voyageNumber, setVoyageNumber] = useState(requestedNumber ?? '')
  const [vesselName, setVesselName] = useState('')
  const [carrierName, setCarrierName] = useState('')
  const [supportedCargoTypes, setSupportedCargoTypes] = useState<RoutingCargoType[]>(['GENERAL'])
  const [movements, setMovements] = useState<MovementInput[]>([emptyMovement()])
  const [invalid, setInvalid] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)
  const [difference, setDifference] = useState<VoyageDifference | null>(null)
  const [registered, setRegistered] = useState<string | null>(null)

  const { data: locations = [] } = useVoyageLocations()
  const { data: existingVoyage } = useVoyage(requestedNumber)

  /**
   * 一覧の「更新する」から来たときは、既存の内容を初期値にする。
   *
   * 番号だけ引き継いで空のフォームを出すと、10 区間ある航海の到着を 1 日ずらすために
   * 全部打ち直すことになり、その過程で別の項目が変わる。
   *
   * 読み込むのは 1 度だけにする。再取得のたびに入れ直すと、直している最中の入力が
   * 黙って元に戻る。
   */
  const [loadedNumber, setLoadedNumber] = useState<string | null>(null)
  if (existingVoyage !== undefined && existingVoyage.voyageNumber !== loadedNumber) {
    setLoadedNumber(existingVoyage.voyageNumber)
    setVesselName(existingVoyage.vesselName)
    setCarrierName(existingVoyage.carrierName)
    setSupportedCargoTypes(existingVoyage.supportedCargoTypes)
    setMovements(
      existingVoyage.movements.map((movement, index) => {
        // 読み込み時に 1 度だけ決まる識別子。以後の追加・削除は入力欄に付いたまま動く
        return {
          key: `${existingVoyage.voyageNumber}-${index}`,
          departureUnLocode: movement.departureUnLocode,
          arrivalUnLocode: movement.arrivalUnLocode,
          departureTime: instantToBusinessLocal(movement.departureTime),
          arrivalTime: instantToBusinessLocal(movement.arrivalTime),
        }
      }),
    )
  }
  const register = useRegisterVoyage()
  const update = useUpdateVoyage()
  const navigate = useNavigate()

  function toRequest(): VoyageRequest {
    return {
      voyageNumber: voyageNumber.trim(),
      vesselName: vesselName.trim(),
      carrierName: carrierName.trim(),
      supportedCargoTypes,
      // 日時は業務の暦で解釈してから送る。toISOString をそのまま使うと、
      // 端末の設定（CI では UTC）で解釈され、入力した時刻とずれる
      movements: movements.map((movement) => ({
        departureUnLocode: movement.departureUnLocode,
        arrivalUnLocode: movement.arrivalUnLocode,
        departureTime: businessLocalToInstant(movement.departureTime),
        arrivalTime: businessLocalToInstant(movement.arrivalTime),
      })),
    }
  }

  async function submit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    setInvalid(null)
    setFailed(false)
    setDifference(null)
    setRegistered(null)

    const message = voyageInvalidMessage({
      voyageNumber,
      vesselName,
      carrierName,
      supportedCargoTypes,
      movements,
    })
    if (message !== null) {
      setInvalid(message)
      return
    }

    try {
      const outcome = await register.mutateAsync(toRequest())
      if (outcome.kind === 'registered') {
        setRegistered(outcome.voyage.voyageNumber)
      } else {
        setDifference(outcome.difference)
      }
    } catch (error) {
      const reason = invalidInputMessage(error)
      if (reason === null) {
        setFailed(true)
      } else {
        setInvalid(reason)
      }
    }
  }

  async function overwrite() {
    setInvalid(null)
    setFailed(false)
    try {
      await update.mutateAsync(toRequest())
      setDifference(null)
      setRegistered(voyageNumber.trim())
    } catch (error) {
      const reason = invalidInputMessage(error)
      if (reason === null) {
        setFailed(true)
      } else {
        setInvalid(reason)
      }
    }
  }

  function toggleCargoType(cargoType: RoutingCargoType) {
    setSupportedCargoTypes((current) =>
      current.includes(cargoType)
        ? current.filter((value) => value !== cargoType)
        : [...current, cargoType],
    )
  }

  function updateMovement(index: number, next: MovementInput) {
    setMovements((current) => current.map((movement, i) => (i === index ? next : movement)))
  }

  function addMovement() {
    // 次の区間は前の到着地から出る。ここを空にすると、必ず入れ直させることになる
    const previous = movements.at(-1)
    setMovements((current) => [...current, emptyMovement(previous?.arrivalUnLocode ?? '')])
  }

  function removeMovement(index: number) {
    setMovements((current) => current.filter((_, i) => i !== index))
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">
          {requestedNumber === null ? '航海スケジュールの登録' : '航海スケジュールの更新'}
        </h1>
        <Link to="/routing/voyages" className="text-blue-600 hover:underline">
          一覧に戻る
        </Link>
      </div>

      {registered !== null && (
        <div className="rounded border border-green-200 bg-green-50 p-4 text-green-800">
          <p>
            航海 {registered} を{requestedNumber === null ? '登録' : '更新'}しました。
          </p>
          <button
            type="button"
            onClick={() => navigate('/routing/voyages')}
            className="mt-2 rounded bg-green-600 px-4 py-2 text-white hover:bg-green-700"
          >
            一覧で確認する
          </button>
        </div>
      )}

      {invalid !== null && (
        <p role="alert" className="rounded border border-red-200 bg-red-50 p-3 text-red-700">
          {invalid}
        </p>
      )}

      {failed && (
        <p role="alert" className="rounded border border-red-200 bg-red-50 p-3 text-red-700">
          登録できませんでした。時間をおいて再度お試しください。
        </p>
      )}

      {difference !== null && (
        <div className="space-y-3 rounded border border-yellow-300 bg-yellow-50 p-4">
          <p className="font-medium text-yellow-900">{difference.message}</p>
          {difference.hasChanges ? (
            <>
              {/* 何が変わるか分からないまま押させない */}
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-yellow-300 text-left">
                    <th className="px-3 py-2">項目</th>
                    <th className="px-3 py-2">今の内容</th>
                    <th className="px-3 py-2">更新後</th>
                  </tr>
                </thead>
                <tbody>
                  {difference.changes.map((change) => (
                    <tr key={change.item} className="border-b border-yellow-200">
                      <td className="px-3 py-2">{change.item}</td>
                      <td className="px-3 py-2">{change.before}</td>
                      <td className="px-3 py-2 font-medium">{change.after}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={overwrite}
                  className="rounded bg-yellow-600 px-4 py-2 text-white hover:bg-yellow-700"
                >
                  この内容で上書きする
                </button>
                <button
                  type="button"
                  onClick={() => setDifference(null)}
                  className="rounded border border-gray-300 bg-white px-4 py-2 text-gray-700 hover:bg-gray-100"
                >
                  やめる
                </button>
              </div>
            </>
          ) : (
            <button
              type="button"
              onClick={() => setDifference(null)}
              className="rounded border border-gray-300 bg-white px-4 py-2 text-gray-700 hover:bg-gray-100"
            >
              閉じる
            </button>
          )}
        </div>
      )}

      <form onSubmit={submit} className="space-y-6">
        <div className="grid gap-4 md:grid-cols-3">
          <div>
            <label htmlFor="voyageNumber" className="block text-sm font-medium text-gray-700">
              航海番号
            </label>
            <input
              id="voyageNumber"
              type="text"
              value={voyageNumber}
              onChange={(event) => setVoyageNumber(event.target.value)}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            />
          </div>

          <div>
            <label htmlFor="vesselName" className="block text-sm font-medium text-gray-700">
              船名
            </label>
            <input
              id="vesselName"
              type="text"
              value={vesselName}
              onChange={(event) => setVesselName(event.target.value)}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            />
          </div>

          <div>
            <label htmlFor="carrierName" className="block text-sm font-medium text-gray-700">
              運送会社
            </label>
            <input
              id="carrierName"
              type="text"
              value={carrierName}
              onChange={(event) => setCarrierName(event.target.value)}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            />
          </div>
        </div>

        <fieldset className="rounded border border-gray-200 p-4">
          <legend className="px-1 text-sm font-medium text-gray-700">運べる貨物</legend>
          <div className="flex flex-wrap gap-4">
            {(Object.keys(ROUTING_CARGO_TYPE_LABELS) as RoutingCargoType[]).map((cargoType) => (
              <label key={cargoType} className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={supportedCargoTypes.includes(cargoType)}
                  onChange={() => toggleCargoType(cargoType)}
                />
                {ROUTING_CARGO_TYPE_LABELS[cargoType]}
              </label>
            ))}
          </div>
        </fieldset>

        <MovementsFieldset
          movements={movements}
          locations={locations}
          onChange={updateMovement}
          onAdd={addMovement}
          onRemove={removeMovement}
        />

        <div className="flex gap-2">
          <button
            type="submit"
            disabled={register.isPending || update.isPending}
            className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
          >
            登録する
          </button>
          <button
            type="button"
            onClick={() => navigate('/routing/voyages')}
            className="rounded border border-gray-300 px-4 py-2 text-gray-700 hover:bg-gray-50"
          >
            キャンセル
          </button>
        </div>
      </form>
    </div>
  )
}
