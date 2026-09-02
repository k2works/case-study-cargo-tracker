import type React from 'react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ShipperFormFields } from '../features/booking/components/shipper-form-fields'
import {
  localInvalidMessage,
  shipperFormValueOf,
  shipperRequestOf,
  type ShipperFormValue,
} from '../features/booking/components/shipper-form-types'
import { invalidInputMessage } from '../features/booking/invalid-input-message'
import { useEditShipper, useShipper } from '../features/booking/queries'

/**
 * 登録済みの荷主を直す（US02 / #550）。
 *
 * 直せないと、転居・改称のたびに同じ荷主をもう 1 件登録することになり、
 * 予約がどちらに紐づくか分からなくなる。
 */
export function ShipperEditPage() {
  const { id: idParam } = useParams()
  const id = Number(idParam)
  const { data: shipper, isPending, isError } = useShipper(id)
  const edit = useEditShipper(id)
  const navigate = useNavigate()

  const [form, setForm] = useState<ShipperFormValue | null>(null)
  const [invalid, setInvalid] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  if (isPending) {
    return <p className="text-gray-600">読み込んでいます…</p>
  }

  if (isError || shipper === undefined) {
    return (
      <div className="space-y-4">
        <p role="alert" className="text-red-700">
          指定された荷主が見つかりません。
        </p>
        <Link to="/booking/shippers" className="text-blue-700 underline">
          荷主一覧へ戻る
        </Link>
      </div>
    )
  }

  // 取得した内容を初期値にし、以後は入力を正とする。取り直しのたびに
  // 初期値へ戻すと、打っている最中の内容が黙って消える
  const value = form ?? shipperFormValueOf(shipper)

  function handleSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    setFailed(false)
    setInvalid(null)

    const localReason = localInvalidMessage(value)
    if (localReason !== null) {
      setInvalid(localReason)
      return
    }

    edit.mutate(shipperRequestOf(value, false), {
      onSuccess: () => navigate('/booking/shippers'),
      onError: (error) => {
        const reason = invalidInputMessage(error)
        if (reason === null) {
          setFailed(true)
        } else {
          setInvalid(reason)
        }
      },
    })
  }

  return (
    <div className="max-w-xl space-y-6">
      <h1 className="text-xl font-bold text-gray-900">荷主の編集</h1>
      <p className="text-sm text-gray-700">
        荷主コード <strong>{shipper.shipperCode}</strong>
        <span className="ml-2 text-gray-600">（コードは変わりません）</span>
      </p>

      <form onSubmit={handleSubmit} className="space-y-4 rounded border bg-white p-6">
        <ShipperFormFields
          value={value}
          typeLocked
          onChange={(next) => {
            setForm(next)
            setInvalid(null)
          }}
        />

        {invalid !== null && (
          <p role="alert" className="text-sm text-red-700">
            {invalid}
          </p>
        )}

        {failed && (
          <p role="alert" className="text-sm text-red-700">
            保存できませんでした。時間をおいて再度お試しください。
          </p>
        )}

        <div className="flex gap-3">
          <button
            type="submit"
            disabled={edit.isPending}
            className="rounded bg-blue-600 px-4 py-2 text-white disabled:bg-gray-300"
          >
            保存する
          </button>
          <Link
            to="/booking/shippers"
            className="rounded border border-gray-400 px-4 py-2 text-sm text-gray-700"
          >
            やめる
          </Link>
        </div>
      </form>
    </div>
  )
}
