import type { Booking } from "../types";
import { formatBusinessDateTime } from "../../../lib/business-time";

/**
 * 割り当てられた旅程（US09）。
 *
 * <p>予約詳細から切り出したのは、割る基準（1 ファイル 400 行）を超えたからではなく、
 * <strong>ここだけが「経路の中身を読む」画面</strong>だからである。手番の操作とは
 * 変わる理由が違う。
 */
export function ItineraryTable({ booking }: Readonly<{ booking: Booking }>) {
  return (
    <>
    {/* 割り当てられた旅程（US09）。**経路が決まっていない予約では枠ごと出さない**。
        空の表を出すと「区間が 0 件の旅程がある」ように見える */}
    {/* null も未設定も「旅程が無い」。項目ごと省く応答もありうる */}
    {(booking.itinerary?.length ?? 0) > 0 && (
      <section className="space-y-2 rounded border border-gray-200 p-4">
        <h2 className="text-lg font-semibold text-gray-900">
          割り当て経路（旅程・{booking.itinerary?.length} 区間）
        </h2>
        <div className="overflow-x-auto">
          <table className="min-w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-gray-300 text-left">
                <th className="py-2">順</th>
                <th>航海</th>
                <th>積込</th>
                <th>荷降し</th>
                <th>積込日時</th>
                <th>荷降し日時</th>
              </tr>
            </thead>
            <tbody>
              {(booking.itinerary ?? []).map((leg, index) => (
                <tr
                  key={`${leg.voyageNumber}-${leg.loadUnLocode}`}
                  className="border-b"
                >
                  <td className="py-2">{index + 1}</td>
                  <td>{leg.voyageNumber}</td>
                  {/* 港は名前で、コードは併記にとどめる（表示規約） */}
                  <td>
                    {leg.loadName}
                    <span className="ml-1 text-gray-500">
                      ({leg.loadUnLocode})
                    </span>
                  </td>
                  <td>
                    {leg.unloadName}
                    <span className="ml-1 text-gray-500">
                      ({leg.unloadUnLocode})
                    </span>
                  </td>
                  {/* 日時は業務タイムゾーン（表示規約） */}
                  <td>{formatBusinessDateTime(leg.loadTime)}</td>
                  <td>{formatBusinessDateTime(leg.unloadTime)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    )}
    </>
  );
}
