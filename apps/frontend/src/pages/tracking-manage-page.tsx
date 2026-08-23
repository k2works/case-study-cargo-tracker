import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useHandlingLocations } from '../features/handling/queries'
import { ExceptionSection } from '../features/tracking/components/exception-section'
import { ManualUpdateForm } from '../features/tracking/components/manual-update-form'
import { TrackingEventsTable } from '../features/tracking/components/tracking-events-table'
import {
  useAdvanceableStatuses,
  useExceptionTypes,
  useManagedTracking,
  useOpenExceptions,
  useRaiseTrackingException,
  useResolveTrackingException,
  useUpdateTrackingStatus,
} from '../features/tracking/queries'
import type { ExceptionType, TrackingStatus } from '../features/tracking/types'
import { ApiError } from '../lib/api-client'
import { businessLocalToInstant, InvalidBusinessDateTimeError } from '../lib/business-time'

/**
 * 貨物状態の管理（US17・US19・US20）。**追跡管理者の担当画面**。
 *
 * IT7 までこのロールは荷役の履歴しか見られなかった。ここが担当画面の本体である。
 */
export function TrackingManagePage() {
  // 一覧から「この貨物を対応する」と辿って来られる。番号を書き写させない
  const [searchParams] = useSearchParams()
  const requested = searchParams.get('trackingNumber')
  const [input, setInput] = useState(requested ?? '')
  const [viewing, setViewing] = useState<string | null>(requested)
  const [message, setMessage] = useState<string | null>(null)
  const [failure, setFailure] = useState<string | null>(null)

  const { data: tracking, error: lookupError } = useManagedTracking(viewing)
  const { data: statuses = [] } = useAdvanceableStatuses(viewing)
  const { data: exceptionTypes = [] } = useExceptionTypes()
  const { data: locations = [] } = useHandlingLocations()
  const { data: openExceptions } = useOpenExceptions()
  const updateStatus = useUpdateTrackingStatus()
  const raiseException = useRaiseTrackingException()
  const resolveException = useResolveTrackingException()

  function report(error: unknown) {
    setFailure(
      error instanceof ApiError
        ? ((error.body as { message?: string } | undefined)?.message ?? '処理できませんでした')
        : '処理できませんでした',
    )
  }

  function begin() {
    setMessage(null)
    setFailure(null)
  }

  function show(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    begin()
    setViewing(input.trim())
  }

  function submitUpdate(input: {
    status: string
    locationUnLocode: string
    occurredAt: string
  }) {
    begin()

    let occurredInstant: string
    try {
      // 日時は業務の暦で解釈してから送る。toISOString をそのまま使うと、
      // 端末の設定（CI では UTC）で解釈され、入力した時刻とずれる
      occurredInstant = businessLocalToInstant(input.occurredAt)
    } catch (error) {
      // **読めない日時で送信そのものを止めない。** 止めると画面には何も出ず、
      // 利用者からは「押しても何も起きない」に見える
      if (error instanceof InvalidBusinessDateTimeError) {
        setFailure('日時を入力してください')
        return
      }
      throw error
    }

    updateStatus.mutate(
      {
        trackingNumber: viewing ?? '',
        status: input.status as TrackingStatus,
        locationUnLocode: input.locationUnLocode,
        occurredAt: occurredInstant,
      },
      { onSuccess: () => setMessage('更新しました。'), onError: report },
    )
  }

  function submitRaise(input: { exceptionType: string; description: string }) {
    begin()
    raiseException.mutate(
      {
        trackingNumber: viewing ?? '',
        exceptionType: input.exceptionType as ExceptionType,
        description: input.description,
      },
      { onSuccess: () => setMessage('起票しました。'), onError: report },
    )
  }

  function submitResolve(input: {
    resolutionNotes: string
    newEstimatedArrival: string | null
  }) {
    begin()
    resolveException.mutate(
      {
        trackingNumber: viewing ?? '',
        exceptionId: tracking?.activeException?.id ?? 0,
        resolutionNotes: input.resolutionNotes,
        newEstimatedArrival: input.newEstimatedArrival,
      },
      { onSuccess: () => setMessage('解決しました。'), onError: report },
    )
  }

  const notFound = lookupError instanceof ApiError && lookupError.status === 404

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">貨物状態の管理</h1>
        <Link to="/dashboard" className="text-blue-600 hover:underline">
          ダッシュボードに戻る
        </Link>
      </div>

      {/* **件数を出すだけにしない。**そこから対象へ行けなければ仕事は進まない（横断規約） */}
      {openExceptions !== undefined && openExceptions.count > 0 && (
        <p className="rounded bg-amber-50 p-3 text-sm text-amber-900">
          未解決の例外が <strong>{openExceptions.count}</strong> 件あります
          {openExceptions.urgentCount > 0 && (
            <>
              （うち<strong>緊急 {openExceptions.urgentCount} 件</strong>）
            </>
          )}
          。{' '}
          <Link to="/tracking/manage/exceptions" className="underline">
            一覧を見る
          </Link>
        </p>
      )}

      <form onSubmit={show} className="flex flex-wrap items-end gap-2">
        <div>
          <label htmlFor="trackingNumber" className="block text-sm font-medium text-gray-700">
            追跡番号
          </label>
          <input
            id="trackingNumber"
            value={input}
            onChange={(event) => setInput(event.target.value)}
            className="mt-1 rounded border border-gray-300 px-3 py-2"
          />
        </div>
        <button type="submit" className="rounded bg-blue-600 px-4 py-2 text-white">
          貨物を表示する
        </button>
      </form>

      {notFound && (
        <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-800">
          追跡番号が見つかりません。
        </p>
      )}

      {message !== null && (
        <p className="rounded bg-green-50 p-3 text-sm text-green-900">{message}</p>
      )}
      {failure !== null && (
        <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-800">
          {failure}
        </p>
      )}

      {tracking !== undefined && (
        <>
          <section className="space-y-2 rounded border border-gray-200 p-4">
            <h2 className="text-lg font-semibold text-gray-900">{tracking.trackingNumber}</h2>
            <dl className="grid gap-2 sm:grid-cols-4">
              <div>
                <dt className="text-sm text-gray-600">状態</dt>
                <dd className="font-medium text-gray-900">{tracking.statusLabel}</dd>
              </div>
              <div>
                <dt className="text-sm text-gray-600">現在地</dt>
                <dd className="font-medium text-gray-900">{tracking.locationName}</dd>
              </div>
              <div>
                <dt className="text-sm text-gray-600">到着予定日</dt>
                <dd className="font-medium text-gray-900">{tracking.estimatedArrival ?? '未定'}</dd>
              </div>
              <div>
                <dt className="text-sm text-gray-600">予約番号</dt>
                <dd className="font-medium text-gray-900">{tracking.bookingId}</dd>
              </div>
            </dl>
            {tracking.activeException !== null && (
              <p role="alert" className="rounded bg-amber-50 p-3 text-sm text-amber-900">
                <strong>例外が起きています。</strong>
                {tracking.activeException.urgent && <strong>緊急</strong>}
                {/* 改行を空白と読ませない（日本語は語間を空けない） */}
                {tracking.activeException.description}
              </p>
            )}
          </section>

          {/* **例外が解決するまで、状態は動かせない。** 動かせると、解決したときに
              戻る先が変わってしまう（[ADR-024] 決定 2） */}
          {tracking.activeException === null && (
            <ManualUpdateForm
              statuses={statuses}
              locations={locations}
              pending={updateStatus.isPending}
              onSubmit={submitUpdate}
            />
          )}

          <ExceptionSection
            tracking={tracking}
            exceptionTypes={exceptionTypes}
            pending={raiseException.isPending || resolveException.isPending}
            onBegin={begin}
            onRaise={submitRaise}
            onResolve={submitResolve}
          />

          <section className="space-y-2">
            <h2 className="text-lg font-semibold text-gray-900">これまでの経過</h2>
            <TrackingEventsTable events={tracking.events} />
          </section>
        </>
      )}
    </div>
  )
}
