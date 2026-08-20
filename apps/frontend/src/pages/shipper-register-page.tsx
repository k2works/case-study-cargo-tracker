import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { registerShipper } from '../features/booking/api'
import { ApiError } from '../lib/api-client'
import {
  SHIPPER_TYPE_LABELS,
  type DuplicateShipper,
  type Shipper,
  type ShipperType,
} from '../features/booking/types'

/** サーバが理由を添えて拒否した（400）ときだけ、その理由を返す。 */
function invalidInputMessage(error: unknown): string | null {
  if (!(error instanceof ApiError) || error.status !== 400) {
    return null
  }
  const body = error.body as { message?: string } | undefined
  return body?.message ?? '入力内容を確認してください。'
}

export function ShipperRegisterPage() {
  const [type, setType] = useState<ShipperType>('INDIVIDUAL')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [address, setAddress] = useState('')
  const [phone, setPhone] = useState('')
  const [contractNumber, setContractNumber] = useState('')
  const [discountRatePercent, setDiscountRatePercent] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [invalid, setInvalid] = useState<string | null>(null)
  const [duplicate, setDuplicate] = useState<DuplicateShipper | null>(null)
  const [registered, setRegistered] = useState<Shipper | null>(null)
  const [failed, setFailed] = useState(false)
  const navigate = useNavigate()

  /**
   * 送信前に、サーバが返すのと同じ文言で拒む。
   *
   * ブラウザ既定の検証（required / max）は吹き出しで知らせるだけで、画面には何も残らない。
   * 「押しても何も起きない」と受け取られ、営業担当者は原因を探せない。
   */
  function localInvalidMessage(): string | null {
    if (type !== 'CORPORATE') {
      return null
    }
    if (contractNumber.trim() === '') {
      return '法人荷主には契約番号が必要です'
    }
    if (discountRatePercent.trim() !== '') {
      const percent = Number(discountRatePercent)
      if (Number.isNaN(percent) || percent < 0 || percent > 30) {
        return `割引率は 0〜30% の範囲で指定してください: ${discountRatePercent}`
      }
    }
    return null
  }

  async function submit(registerAnyway: boolean) {
    setFailed(false)
    setInvalid(null)

    const localReason = localInvalidMessage()
    if (localReason !== null) {
      setInvalid(localReason)
      return
    }

    setSubmitting(true)
    try {
      const outcome = await registerShipper({
        type,
        name,
        email,
        address,
        phone: phone.trim() === '' ? null : phone,
        contractNumber: type === 'CORPORATE' && contractNumber.trim() !== '' ? contractNumber : null,
        discountRatePercent:
          type === 'CORPORATE' && discountRatePercent.trim() !== ''
            ? Number(discountRatePercent)
            : null,
        registerAnyway,
      })

      if (outcome.kind === 'duplicate') {
        // 「登録できません」で終わらせない。営業担当者が次に選べる形にする
        setDuplicate(outcome.duplicate)
        return
      }

      setDuplicate(null)
      setRegistered(outcome.shipper)
    } catch (error) {
      // 理由の分かる拒否（400）は「時間をおいて再度」ではなく、直すべき箇所を示す
      const reason = invalidInputMessage(error)
      if (reason !== null) {
        setInvalid(reason)
      } else {
        setFailed(true)
      }
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
              setContractNumber('')
              setDiscountRatePercent('')
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
            onChange={(event) => {
              // 個人に戻したら契約情報は捨てる。残すと、画面に出ていない値が
              // 次に法人へ切り替えたときに黙って復活する
              setType(event.target.value as ShipperType)
              setContractNumber('')
              setDiscountRatePercent('')
              setInvalid(null)
            }}
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          >
            {Object.entries(SHIPPER_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>

        {type === 'CORPORATE' && (
          <fieldset className="space-y-4 rounded border border-gray-200 bg-gray-50 p-4">
            <legend className="px-1 text-sm font-medium text-gray-700">法人契約情報</legend>

            <div>
              <label htmlFor="contractNumber" className="block text-sm font-medium text-gray-700">
                契約番号
              </label>
              <input
                id="contractNumber"
                type="text"
                value={contractNumber}
                onChange={(event) => setContractNumber(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
              <p className="mt-1 text-sm text-gray-500">
                法人には契約番号が必要です。空のまま登録すると、精算時に契約が特定できません。
              </p>
            </div>

            <div>
              <label
                htmlFor="discountRatePercent"
                className="block text-sm font-medium text-gray-700"
              >
                割引率（%）
              </label>
              <input
                id="discountRatePercent"
                type="number"
                step="0.1"
                value={discountRatePercent}
                onChange={(event) => setDiscountRatePercent(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
              <p className="mt-1 text-sm text-gray-500">
                0〜30% の範囲。交渉中なら空のままにします（空欄は 0% ではなく「未設定」です）。
              </p>
            </div>
          </fieldset>
        )}

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

        {invalid !== null && (
          <p role="alert" className="text-sm text-red-700">
            {invalid}
          </p>
        )}

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
