import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { registerShipper } from '../features/booking/api'
import {
  SHIPPER_TYPE_LABELS,
  type DuplicateShipper,
  type Shipper,
  type ShipperType,
} from '../features/booking/types'

export function ShipperRegisterPage() {
  const [type, setType] = useState<ShipperType>('INDIVIDUAL')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [address, setAddress] = useState('')
  const [phone, setPhone] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [duplicate, setDuplicate] = useState<DuplicateShipper | null>(null)
  const [registered, setRegistered] = useState<Shipper | null>(null)
  const [failed, setFailed] = useState(false)
  const navigate = useNavigate()

  async function submit(registerAnyway: boolean) {
    setFailed(false)
    setSubmitting(true)
    try {
      const outcome = await registerShipper({
        type,
        name,
        email,
        address,
        phone: phone.trim() === '' ? null : phone,
        registerAnyway,
      })

      if (outcome.kind === 'duplicate') {
        // 「登録できません」で終わらせない。営業担当者が次に選べる形にする
        setDuplicate(outcome.duplicate)
        return
      }

      setDuplicate(null)
      setRegistered(outcome.shipper)
    } catch {
      setFailed(true)
    } finally {
      setSubmitting(false)
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    void submit(false)
  }

  if (registered !== null) {
    return (
      <div className="space-y-4">
        <h1 className="text-xl font-bold text-gray-900">荷主を登録しました</h1>
        <p className="text-gray-700">
          荷主コード <strong>{registered.shipperCode}</strong> を発行しました（
          {registered.name}）。
        </p>
        <div className="flex gap-4">
          <Link to="/booking/shippers" className="text-blue-700 underline">
            荷主一覧へ戻る
          </Link>
          <button
            type="button"
            className="text-blue-700 underline"
            onClick={() => {
              setRegistered(null)
              setName('')
              setEmail('')
              setAddress('')
              setPhone('')
            }}
          >
            続けて登録する
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-xl space-y-6">
      <h1 className="text-xl font-bold text-gray-900">荷主登録</h1>

      <form onSubmit={handleSubmit} className="space-y-4 rounded border bg-white p-6">
        <div>
          <label htmlFor="type" className="block text-sm font-medium text-gray-700">
            荷主種別
          </label>
          <select
            id="type"
            value={type}
            onChange={(event) => setType(event.target.value as ShipperType)}
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          >
            {Object.entries(SHIPPER_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          {type === 'CORPORATE' && (
            <p className="mt-1 text-sm text-gray-500">
              契約番号と割引率の登録は次のリリースで対応します。
            </p>
          )}
        </div>

        <div>
          <label htmlFor="name" className="block text-sm font-medium text-gray-700">
            氏名/社名
          </label>
          <input
            id="name"
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            required
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          />
        </div>

        <div>
          <label htmlFor="email" className="block text-sm font-medium text-gray-700">
            メールアドレス
          </label>
          <input
            id="email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          />
        </div>

        <div>
          <label htmlFor="address" className="block text-sm font-medium text-gray-700">
            住所
          </label>
          <input
            id="address"
            type="text"
            value={address}
            onChange={(event) => setAddress(event.target.value)}
            required
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          />
        </div>

        <div>
          <label htmlFor="phone" className="block text-sm font-medium text-gray-700">
            連絡先（任意）
          </label>
          <input
            id="phone"
            type="tel"
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          />
        </div>

        {failed && (
          <p role="alert" className="text-sm text-red-700">
            登録できませんでした。時間をおいて再度お試しください。
          </p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded bg-blue-600 px-4 py-2 text-white disabled:bg-gray-300"
        >
          登録する
        </button>
      </form>

      {duplicate !== null && (
        <section className="rounded border border-amber-300 bg-amber-50 p-6">
          <h2 className="font-semibold text-gray-900">{duplicate.message}</h2>

          <dl className="mt-4 space-y-1 text-sm text-gray-800">
            <div className="flex gap-2">
              <dt className="w-28 text-gray-600">荷主コード</dt>
              <dd>{duplicate.existing.shipperCode}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="w-28 text-gray-600">種別</dt>
              {/* 個人か法人かは「同じ相手か別会社か」を判断する一番大きな手がかり */}
              <dd>{SHIPPER_TYPE_LABELS[duplicate.existing.type]}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="w-28 text-gray-600">氏名/社名</dt>
              <dd>{duplicate.existing.name}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="w-28 text-gray-600">住所</dt>
              <dd>{duplicate.existing.address}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="w-28 text-gray-600">連絡先</dt>
              <dd>{duplicate.existing.phone ?? '—'}</dd>
            </div>
          </dl>

          <div className="mt-4 flex gap-3">
            <button
              type="button"
              className="rounded bg-blue-600 px-4 py-2 text-sm text-white"
              onClick={() =>
                // その荷主に絞り込んで戻す。絞り込まないと、営業は用のある荷主を
                // 全件から探し直すことになる
                navigate(`/booking/shippers?keyword=${encodeURIComponent(duplicate.existing.email)}`)
              }
            >
              既存の荷主を使う
            </button>
            <button
              type="button"
              disabled={submitting}
              className="rounded border border-gray-400 px-4 py-2 text-sm disabled:text-gray-400"
              onClick={() => void submit(true)}
            >
              それでも新規で登録する
            </button>
          </div>
        </section>
      )}
    </div>
  )
}
