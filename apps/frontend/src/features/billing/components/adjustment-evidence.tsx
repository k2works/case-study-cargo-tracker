import { formatBusinessDateTime } from '../../../lib/business-time'
import { portLabel } from '../../../lib/port-label'
import type { ChargeCalculation } from '../types'

/**
 * 料金調整の根拠（US21-6）。
 *
 * <p><strong>「残っている」と「読める」は別である</strong>（IT10 レビューの懸念）。
 * 誤配の記録は IT10 で予約に残したが、出ていたのは予約詳細だけで、
 * <strong>経理担当者はその画面を開けなかった</strong>。ここで初めて読まれる。
 *
 * <p><strong>金額は自動で決めない</strong>（[ADR-027] 決定 6）。どれだけ減額するかは
 * 荷主との関係で決まる話であり、規則にできない。画面が出すのは根拠までである。
 */
export function AdjustmentEvidence({
  misroute,
  exceptions,
}: Readonly<{
  misroute: ChargeCalculation['misroute']
  exceptions: ChargeCalculation['exceptions']
}>) {
  const hasEvidence = misroute !== null || exceptions.length > 0

  if (!hasEvidence) {
    return (
      <section aria-labelledby="evidence-heading" className="space-y-2">
        <h2 id="evidence-heading" className="text-lg font-semibold">
          調整の根拠
        </h2>
        {/* **断定しない**（IT11 レビュー 高 2）。遅延・破損の例外は trackingms にあるが、
            本 IT では引いていない。「例外の記録はありません」と言い切ると、
            例外がある貨物でも経理担当者はこの文言を読んで調整を見送る */}
        <p className="text-gray-700">
          {'この貨物に誤配の記録はありません。'}
          <strong>{'遅延・破損などの例外は、この画面には表示されません'}</strong>
          {'——追跡の画面で確かめてください。'}
        </p>
      </section>
    )
  }

  return (
    <section aria-labelledby="evidence-heading" className="space-y-2">
      <h2 id="evidence-heading" className="text-lg font-semibold">
        調整の根拠
      </h2>

      <div
        className="space-y-2 rounded border border-amber-300 bg-amber-50 p-4"
        data-testid="adjustment-evidence"
      >
        {/* **例外は本 IT では引いていない**（受入基準 21-6 の片肺）。
            出していないことを言わないと、経理担当者は「例外は無かった」と読む */}
        <p className="text-sm text-amber-900">
          {'遅延・破損などの例外は、この画面には表示されません（追跡の画面で確かめてください）。'}
        </p>
        {misroute !== null && (
          <p>
            <strong>誤配</strong>：
            {portLabel(misroute.locationUnLocode, misroute.locationName, '（不明）')}
            で予定ルートから外れました（{formatBusinessDateTime(misroute.at)}）。
          </p>
        )}
        {exceptions.map((exception) => (
          <p key={`${exception.type}-${exception.occurredAt}`}>
            <strong>{exception.typeLabel}</strong>：{exception.description}（
            {formatBusinessDateTime(exception.occurredAt)}）
          </p>
        ))}
      </div>
    </section>
  )
}
