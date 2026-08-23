import { useState } from 'react'
import { Link } from 'react-router-dom'
import { HandlingHistoryTable } from '../features/handling/components/handling-history-table'
import {
  useHandlingHistory,
  useHandlingLocations,
  useHandlingTypes,
  useRegisterHandlingActivity,
} from '../features/handling/queries'
import type { HandlingActivity } from '../features/handling/types'
import { useAuthStore } from '../stores/auth-store'
import { ApiError } from '../lib/api-client'
import { InvalidBusinessDateTimeError, businessLocalToInstant, instantToBusinessLocal } from '../lib/business-time'

/**
 * 荷役作業の記録（US15・US16）。
 *
 * 荷役作業員は**追跡番号を起点**に作業する。予約番号は知らない。手元にあるのは貨物に
 * 貼られた追跡番号である。
 *
 * **登録後もフォームを空にしない。** 同じ貨物に続けて記録するのが荷役の実際の使い方で、
 * 全部空にすると作業員は追跡番号を毎回打ち直すことになる。
 */
export function HandlingPage() {
  const { data: types = [] } = useHandlingTypes()
  const { data: locations = [] } = useHandlingLocations()
  const register = useRegisterHandlingActivity()

  // 記録できるのは荷役作業員だけ（[ADR-008]）。追跡管理者は参照のみで、
  // **押せるのに断られる操作を出さない**
  const isHandler = useAuthStore((state) => state.hasAnyRole(['ROLE_HANDLER']))

  const [trackingNumber, setTrackingNumber] = useState('')
  const [type, setType] = useState('RECEIVE')
  const [locationUnLocode, setLocationUnLocode] = useState('')
  // 港の記録はほぼ「いま」である。空から始めると、1 日数十件を打つ人にとって
  // 一番手数の多い欄になる
  const [completionTime, setCompletionTime] = useState(() =>
    instantToBusinessLocal(new Date().toISOString()),
  )
  const [voyageNumber, setVoyageNumber] = useState('')
  const [consigneeConfirmation, setConsigneeConfirmation] = useState('')
  const [registered, setRegistered] = useState<HandlingActivity | null>(null)
  const [invalid, setInvalid] = useState<string | null>(null)
  const [viewing, setViewing] = useState<string | null>(null)

  // **記録できたかどうかと切り離す。** 追跡管理者は記録できないため、記録の成否に
  // 紐づけると履歴を一度も見られない
  const { data: history = [], error: historyError } = useHandlingHistory(viewing)

  // **要件はサーバが答える**（[ADR-023] 決定 1）。画面が「積込なら航海番号が要る」と
  // 書くと、規則が種別と画面の 2 か所に分かれ、片方だけ直る形になる
  const selectedType = types.find((candidate) => candidate.type === type)

  function submit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    setRegistered(null)
    setInvalid(null)

    let completionInstant: string
    try {
      // 日時は業務の暦で解釈してから送る。toISOString をそのまま使うと、
      // 端末の設定（CI では UTC）で解釈され、入力した時刻とずれる
      completionInstant = businessLocalToInstant(completionTime)
    } catch (error) {
      // **読めない日時で送信そのものを止めない。** 止めると画面には何も出ず、
      // 利用者からは「押しても何も起きない」に見える
      if (error instanceof InvalidBusinessDateTimeError) {
        setInvalid('作業日時を入力してください')
        return
      }
      throw error
    }

    register.mutate(
      {
        trackingNumber: trackingNumber.trim(),
        type,
        locationUnLocode,
        completionTime: completionInstant,
        voyageNumber: voyageNumber.trim() === '' ? null : voyageNumber.trim(),
        consigneeConfirmation:
          consigneeConfirmation.trim() === '' ? null : consigneeConfirmation.trim(),
      },
      {
        onSuccess: (activity) => {
          setRegistered(activity)
          setViewing(trackingNumber.trim())
          // 追跡番号と作業場所は残す。同じ貨物に続けて記録するのが実際の使い方である。
          // **日時は「いま」に戻す。**前の作業の時刻のまま次を記録する事故を防ぐ
          setCompletionTime(instantToBusinessLocal(new Date().toISOString()))
          setVoyageNumber('')
          setConsigneeConfirmation('')
        },
      },
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">荷役作業の記録</h1>
        <Link to="/dashboard" className="text-blue-600 hover:underline">
          ダッシュボードに戻る
        </Link>
      </div>

      <p className="text-sm text-gray-700">
        貨物に貼られた<strong>追跡番号</strong>から記録します。
        {/* 改行を空白と読ませない（日本語は語間を空けない） */}
        記録すると貨物の追跡状態が変わります。
        {/* 同上 */}
        <strong>荷主へは自動で通知されません。</strong>
        {/* 同上 */}
        連絡が必要なときは営業担当者へ伝えてください（通知の仕組みは次のリリースです）。
      </p>

      {/* **記録できない人も使える入口。** 追跡管理者は「あの貨物はもう積んだか」に
          答えるためにここへ来る。記録の成否に紐づけると、履歴を一度も見られない */}
      <section className="space-y-2 rounded border border-gray-200 bg-gray-50 p-4">
        <h2 className="text-lg font-semibold text-gray-900">この貨物の作業履歴を見る</h2>
        <div className="flex flex-wrap items-end gap-2">
          <div>
            <label htmlFor="historyTrackingNumber" className="block text-sm font-medium text-gray-700">
              追跡番号
            </label>
            <input
              id="historyTrackingNumber"
              value={trackingNumber}
              onChange={(event) => setTrackingNumber(event.target.value)}
              className="mt-1 rounded border border-gray-300 px-3 py-2"
            />
          </div>
          <button
            type="button"
            onClick={() => setViewing(trackingNumber.trim())}
            className="rounded border border-gray-400 px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
          >
            履歴を見る
          </button>
        </div>
        {historyError !== null && historyError !== undefined && (
          <p role="alert" className="text-sm text-red-800">
            {historyError instanceof ApiError
              ? ((historyError.body as { message?: string } | undefined)?.message
                ?? '履歴を取得できませんでした')
              : '履歴を取得できませんでした'}
          </p>
        )}
      </section>

      {isHandler && (
      <form onSubmit={submit} className="space-y-4 rounded border border-gray-200 p-4">
        <h2 className="text-lg font-semibold text-gray-900">作業を記録する</h2>
        <p className="text-sm text-gray-600">
          上の追跡番号の貨物に記録します。
        </p>
        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <label htmlFor="type" className="block text-sm font-medium text-gray-700">
              作業の種別
            </label>
            <select
              id="type"
              value={type}
              onChange={(event) => setType(event.target.value)}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            >
              {types.map((choice) => (
                <option key={choice.type} value={choice.type}>
                  {choice.label}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="locationUnLocode" className="block text-sm font-medium text-gray-700">
              作業場所
            </label>
            {/* 自由入力にしない。綴りの揺れた港が記録に入ると、照合が働かなくなる（US15-3） */}
            <select
              id="locationUnLocode"
              value={locationUnLocode}
              onChange={(event) => setLocationUnLocode(event.target.value)}
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

          <div>
            <label htmlFor="completionTime" className="block text-sm font-medium text-gray-700">
              作業日時
            </label>
            <input
              id="completionTime"
              type="datetime-local"
              value={completionTime}
              onChange={(event) => setCompletionTime(event.target.value)}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            />
          </div>

          {/* 航海番号が要るかはサーバが答える。積込・荷降しは、どの船に載せたかが
              分からないと貨物を追えない */}
          {selectedType?.requiresVoyageNumber === true && (
            <div>
              <label htmlFor="voyageNumber" className="block text-sm font-medium text-gray-700">
                航海番号
              </label>
              <input
                id="voyageNumber"
                value={voyageNumber}
                onChange={(event) => setVoyageNumber(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
            </div>
          )}

          {selectedType?.requiresConsigneeConfirmation === true && (
            <div className="md:col-span-2">
              <label
                htmlFor="consigneeConfirmation"
                className="block text-sm font-medium text-gray-700"
              >
                荷受人の確認（誰から受け取りの確認を得たか）
              </label>
              <input
                id="consigneeConfirmation"
                value={consigneeConfirmation}
                onChange={(event) => setConsigneeConfirmation(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
              {/* **代替であることを書く**（[ADR-023] 決定 4）。書かないと、作業員は
                  「システムが通関を見ている」と受け取る */}
              <p className="mt-1 text-sm text-amber-800">
                <strong>通関の確認は、まだ仕組みでは行われません。</strong>
                {/* 改行を空白と読ませない（日本語は語間を空けない） */}
                引き渡してよいかは、通関の書類を確かめてから記録してください（仕組みでの確認は次のリリース以降です）。
              </p>
            </div>
          )}
        </div>

        {invalid !== null && (
          <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-800">
            {invalid}
          </p>
        )}

        {register.error !== null && register.error !== undefined && (
          <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-800">
            {register.error instanceof ApiError
              ? ((register.error.body as { message?: string } | undefined)?.message ??
                '記録できませんでした')
              : '記録できませんでした'}
          </p>
        )}

        {registered !== null && (
          <div className="space-y-1 rounded bg-green-50 p-3 text-sm text-green-900">
            <p>記録しました。</p>
            {/* 予定外でも記録は拒まない（[ADR-023] 決定 3）。警告は記録したあとに出す */}
            {/* **気づく手段は次の行動へ繋ぐ。**「担当者に確認」だけでは、港の作業員は
                誰にどう連絡するのか分からない */}
            {registered.offRoute && (
              <p role="alert" className="text-amber-900">
                <strong>予定と違う場所での作業です。</strong>
                {/* 改行を空白と読ませない（日本語は語間を空けない） */}
                記録は残しました。<strong>追跡管理者</strong>へ連絡してください
                （経路の見直しが要るかを判断します）。
              </p>
            )}
          </div>
        )}

        <button
          type="submit"
          disabled={register.isPending}
          className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
        >
          記録する
        </button>
      </form>
      )}

      {viewing !== null && viewing !== '' && (
        <section className="space-y-2">
          {/* 見出しは追跡番号。この画面の前提（追跡番号が起点）と揃える。
              作業員は予約番号を知らない */}
          <h2 className="text-lg font-semibold text-gray-900">作業履歴（{viewing}）</h2>
          <HandlingHistoryTable activities={history} />
        </section>
      )}
    </div>
  )
}
