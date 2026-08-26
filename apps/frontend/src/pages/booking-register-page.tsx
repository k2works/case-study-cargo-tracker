import type React from 'react'
import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  HazardousFields,
  TemperatureFields,
} from '../features/booking/components/booking-form-fields'
import {
  EMPTY_HAZARDOUS,
  EMPTY_TEMPERATURE,
  additionalFieldsOf,
  type HazardousInput,
  type TemperatureInput,
} from '../features/booking/components/booking-form-types'
import {
  useBookCargo,
  useHazardClasses,
  useLocations,
  useShippers,
} from '../features/booking/queries'
import { differencesFromEstimate } from '../features/booking/estimate-match'
import { useEstimate } from '../features/booking/estimate-queries'
import { CARGO_TYPE_LABELS, type CargoType } from '../features/booking/types'
import { ApiError } from '../lib/api-client'

/** サーバが理由を添えて拒んだ（400）ときだけ、その理由を返す。 */
function invalidInputMessage(error: unknown): string | null {
  if (!(error instanceof ApiError) || error.status !== 400) {
    return null
  }
  const body = error.body as { message?: string } | undefined
  return body?.message ?? '入力内容を確認してください。'
}

function numberOrNull(value: string): number | null {
  return value.trim() === '' ? null : Number(value)
}

function textOrNull(value: string): string | null {
  return value.trim() === '' ? null : value.trim()
}

export function BookingRegisterPage() {
  const [shipperId, setShipperId] = useState('')
  const [type, setType] = useState<CargoType>('GENERAL')
  const [weightKg, setWeightKg] = useState('')
  const [quantity, setQuantity] = useState('')
  const [description, setDescription] = useState('')
  const [lengthCm, setLengthCm] = useState('')
  const [widthCm, setWidthCm] = useState('')
  const [heightCm, setHeightCm] = useState('')
  const [originUnLocode, setOriginUnLocode] = useState('')
  const [destinationUnLocode, setDestinationUnLocode] = useState('')
  const [departureDate, setDepartureDate] = useState('')
  const [arrivalDeadline, setArrivalDeadline] = useState('')
  const [hazardous, setHazardous] = useState<HazardousInput>(EMPTY_HAZARDOUS)
  const [temperature, setTemperature] = useState<TemperatureInput>(EMPTY_TEMPERATURE)
  const [invalid, setInvalid] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)
  /**
   * 見積との食い違い（受入基準 01-7・US04 の未達）。
   *
   * <p><strong>断らない。</strong>条件が変わること自体は業務として普通に起きる
   * （荷主が数量を増やす）。営業担当者が気づいて荷主に確かめられればよい。
   */
  const [mismatch, setMismatch] = useState<string[] | null>(null)

  const navigate = useNavigate()
  const { data: shippers = [] } = useShippers('')
  const { data: locations = [] } = useLocations()
  const hazardClasses = useHazardClasses()
  const { mutateAsync: book, isPending } = useBookCargo()

  const additional = additionalFieldsOf(type)

  // **見積から来たときだけ突き合わせる。**見積を使わない予約は今までどおり
  const [params] = useSearchParams()
  const estimateId = params.get('estimateId') ?? ''
  const { data: estimate } = useEstimate(estimateId)

  /**
   * 見積の条件を初期値にする。**打ち直させると、そこで食い違いが生まれる。**
   *
   * <p><strong>描画中に入れる</strong>（効果では入れない）。効果で入れると、
   * 一度は空の入力欄が描かれてから値が入り、営業担当者の入力とぶつかりうる。
   * 1 つの見積につき 1 度だけ入れる——2 度目に入れると、直した値が戻る。
   */
  const [seededEstimateId, setSeededEstimateId] = useState('')
  if (estimate !== undefined && seededEstimateId !== estimate.estimateId) {
    setSeededEstimateId(estimate.estimateId)
    setOriginUnLocode(estimate.originUnLocode)
    setDestinationUnLocode(estimate.destinationUnLocode)
    setArrivalDeadline(estimate.arrivalDeadline)
    setType(estimate.cargoType)
    setWeightKg(String(estimate.weightKg))
  }

  /**
   * 種別を変えたら、前の種別で入れた追加情報は捨てる。
   *
   * 残すと、画面に出ていない値が黙って送られる。一般貨物に危険物申告が付いた予約は
   * サーバが拒むため、営業担当者には「入力していない項目のせいで登録できない」と見える。
   */
  function changeType(next: CargoType) {
    setType(next)
    setHazardous(EMPTY_HAZARDOUS)
    setTemperature(EMPTY_TEMPERATURE)
    setInvalid(null)
  }

  async function handleSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    setInvalid(null)
    setFailed(false)

    if (shipperId === '') {
      setInvalid('荷主を選んでください')
      return
    }
    if (originUnLocode === '' || destinationUnLocode === '') {
      setInvalid('出発地と目的地を選んでください')
      return
    }
    if (arrivalDeadline === '') {
      setInvalid('到着期限を入力してください')
      return
    }

    // **見積と食い違っていたら、まず知らせる**（受入基準 01-7・US04 の未達）。
    // 2 度目の送信では登録する——確かめたうえで進めるのは営業担当者の判断である。
    //
    // **押す前に条件を直すのが普通の流れである。**直したら判定し直す
    // ——出した警告をそのままにすると、**直した内容が無警告で通る**
    // （IT12 レビュー・user 中）
    const differences = differencesFromEstimate(estimate, {
      originUnLocode,
      destinationUnLocode,
      arrivalDeadline,
      cargoType: type,
      weightKg,
    })
    if (differences.length === 0) {
      setMismatch(null)
    } else if (mismatch === null || mismatch.join() !== differences.join()) {
      // 初めての警告、または前に出した警告と中身が変わった（条件を直した）
      setMismatch(differences)
      return
    }

    try {
      const booked = await book({
        shipperId: Number(shipperId),
        type,
        weightKg: Number(weightKg),
        quantity: numberOrNull(quantity),
        description: textOrNull(description),
        lengthCm: numberOrNull(lengthCm),
        widthCm: numberOrNull(widthCm),
        heightCm: numberOrNull(heightCm),
        originUnLocode,
        destinationUnLocode,
        departureDate: textOrNull(departureDate),
        arrivalDeadline,
        hazardousClass: additional.hazardous ? textOrNull(hazardous.hazardousClass) : null,
        unNumber: additional.hazardous ? textOrNull(hazardous.unNumber) : null,
        properShippingName: additional.hazardous
          ? textOrNull(hazardous.properShippingName)
          : null,
        minCelsius: additional.temperature ? numberOrNull(temperature.minCelsius) : null,
        maxCelsius: additional.temperature ? numberOrNull(temperature.maxCelsius) : null,
      })

      // 予約詳細は IT3 以降。登録完了は一覧に戻し、採番された番号をそこで示す
      navigate(`/booking?registered=${encodeURIComponent(booked.bookingId)}`)
    } catch (error) {
      const reason = invalidInputMessage(error)
      if (reason === null) {
        setFailed(true)
      } else {
        setInvalid(reason)
      }
    }
  }

  return (
    <div className="max-w-2xl space-y-6">
      <h1 className="text-xl font-bold text-gray-900">貨物予約の登録</h1>

      {/* **どの見積から来たかを出す。**出さないと、営業担当者は突き合わせの相手を
          確かめられない */}
      {estimate !== undefined && (
        <p className="rounded border border-gray-300 bg-gray-50 p-3 text-sm">
          {`見積 ${estimate.estimateNumber} の内容で入力しています。`}
        </p>
      )}

      {/* **断らずに知らせる**（受入基準 01-7）。条件が変わること自体は普通に起きる */}
      {mismatch !== null && mismatch.length > 0 && (
        <p
          role="alert"
          className="rounded border border-amber-300 bg-amber-50 p-3 text-sm"
          data-testid="estimate-mismatch"
        >
          <strong>{`見積 ${estimate?.estimateNumber ?? ''} と食い違っています。`}</strong>
          {`食い違っている項目: ${mismatch.join('・')}。`}
          {'荷主にご確認のうえ、そのまま登録する場合はもう一度「登録する」を押してください。'}
        </p>
      )}

      <form onSubmit={handleSubmit} className="space-y-4 rounded border bg-white p-6">
        <div>
          <label htmlFor="shipperId" className="block text-sm font-medium text-gray-700">
            荷主
          </label>
          {/* ID の直接入力はさせない。打ち間違いが登録の失敗としてしか返らない */}
          <select
            id="shipperId"
            value={shipperId}
            onChange={(event) => setShipperId(event.target.value)}
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          >
            <option value="">選んでください</option>
            {shippers.map((shipper) => (
              <option key={shipper.id} value={shipper.id}>
                {shipper.name}（{shipper.shipperCode}）
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="type" className="block text-sm font-medium text-gray-700">
            貨物種別
          </label>
          <select
            id="type"
            value={type}
            onChange={(event) => changeType(event.target.value as CargoType)}
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          >
            {Object.entries(CARGO_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>

        <fieldset className="space-y-4 rounded border border-gray-200 bg-gray-50 p-4">
          <legend className="px-1 text-sm font-medium text-gray-700">輸送条件</legend>

          <div className="flex gap-4">
            <div className="flex-1">
              <label htmlFor="originUnLocode" className="block text-sm font-medium text-gray-700">
                出発地
              </label>
              <select
                id="originUnLocode"
                value={originUnLocode}
                onChange={(event) => setOriginUnLocode(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              >
                <option value="">選んでください</option>
                {locations.map((location) => (
                  <option key={location.unLocode} value={location.unLocode}>
                    {location.name}（{location.unLocode}）
                  </option>
                ))}
              </select>
            </div>
            <div className="flex-1">
              <label
                htmlFor="destinationUnLocode"
                className="block text-sm font-medium text-gray-700"
              >
                目的地
              </label>
              <select
                id="destinationUnLocode"
                value={destinationUnLocode}
                onChange={(event) => setDestinationUnLocode(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              >
                <option value="">選んでください</option>
                {locations.map((location) => (
                  <option key={location.unLocode} value={location.unLocode}>
                    {location.name}（{location.unLocode}）
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="flex gap-4">
            <div className="flex-1">
              <label htmlFor="departureDate" className="block text-sm font-medium text-gray-700">
                希望出発日
              </label>
              <input
                id="departureDate"
                type="date"
                value={departureDate}
                onChange={(event) => setDepartureDate(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
            </div>
            <div className="flex-1">
              <label htmlFor="arrivalDeadline" className="block text-sm font-medium text-gray-700">
                到着期限
              </label>
              <input
                id="arrivalDeadline"
                type="date"
                value={arrivalDeadline}
                onChange={(event) => setArrivalDeadline(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
              {/* 期限は目的地の暦で判断する（ADR-010） */}
              <p className="mt-1 text-sm text-gray-500">目的地の暦で判断します。</p>
            </div>
          </div>
        </fieldset>

        <fieldset className="space-y-4 rounded border border-gray-200 bg-gray-50 p-4">
          <legend className="px-1 text-sm font-medium text-gray-700">貨物仕様</legend>

          <div className="flex gap-4">
            <div className="flex-1">
              <label htmlFor="weightKg" className="block text-sm font-medium text-gray-700">
                重量（kg）
              </label>
              <input
                id="weightKg"
                type="number"
                step="any"
                value={weightKg}
                onChange={(event) => setWeightKg(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
            </div>
            <div className="flex-1">
              <label htmlFor="quantity" className="block text-sm font-medium text-gray-700">
                個数
              </label>
              <input
                id="quantity"
                type="number"
                value={quantity}
                onChange={(event) => setQuantity(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
            </div>
          </div>

          <div className="flex gap-4">
            <div className="flex-1">
              <label htmlFor="lengthCm" className="block text-sm font-medium text-gray-700">
                長さ（cm）
              </label>
              <input
                id="lengthCm"
                type="number"
                step="any"
                value={lengthCm}
                onChange={(event) => setLengthCm(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
            </div>
            <div className="flex-1">
              <label htmlFor="widthCm" className="block text-sm font-medium text-gray-700">
                幅（cm）
              </label>
              <input
                id="widthCm"
                type="number"
                step="any"
                value={widthCm}
                onChange={(event) => setWidthCm(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
            </div>
            <div className="flex-1">
              <label htmlFor="heightCm" className="block text-sm font-medium text-gray-700">
                高さ（cm）
              </label>
              <input
                id="heightCm"
                type="number"
                step="any"
                value={heightCm}
                onChange={(event) => setHeightCm(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
            </div>
          </div>

          <div>
            <label htmlFor="description" className="block text-sm font-medium text-gray-700">
              品名
            </label>
            <input
              id="description"
              type="text"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            />
          </div>
        </fieldset>

        {additional.hazardous && (
          <HazardousFields
            value={hazardous}
            onChange={setHazardous}
            hazardClasses={hazardClasses.data ?? []}
          />
        )}
        {additional.temperature && (
          <TemperatureFields value={temperature} onChange={setTemperature} />
        )}

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

        <div className="flex gap-3">
          <button
            type="submit"
            disabled={isPending}
            className="rounded bg-blue-600 px-4 py-2 text-white disabled:bg-gray-300"
          >
            登録する
          </button>
          <button
            type="button"
            onClick={() => navigate('/booking')}
            className="rounded border border-gray-400 px-4 py-2"
          >
            キャンセル
          </button>
        </div>
      </form>
    </div>
  )
}
