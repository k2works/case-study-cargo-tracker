import type { UseMutationResult } from "@tanstack/react-query";
import { ApiError } from "../../../lib/api-client";
import type { Booking } from "../types";
import { can } from "../types";

/**
 * 日程の訂正（US06 の訂正）。営業担当者の手番。
 *
 * <p>条件協議の結果が「期限を延ばす」だったとき、<strong>予約を直せないと再依頼しても
 * 同じ結果になる</strong>。営業は予約を作り直すことになり、予約番号が変わって他サービスの
 * 参照が外れる（[ADR-011]）。
 *
 * <p>直せる場面の判定は<strong>サーバが返す「行える操作」</strong>に従う。ここで状態名を
 * 見比べると、遷移の規則が画面にも住み着く。
 */
export function ScheduleRevisionSection({
  booking,
  isSales,
  revise,
  revising,
  setRevising,
}: Readonly<{
  booking: Booking;
  isSales: boolean;
  revise: UseMutationResult<
    unknown,
    unknown,
    { departureDate: string | null; arrivalDeadline: string },
    unknown
  >;
  revising: boolean;
  setRevising: (revising: boolean) => void;
}>) {
  return (
    <>
    {/* 日程の訂正（US06 の訂正）。**引き渡す前か、営業へ戻された予約だけ**。
        経路設計者が組んでいる最中に条件が変わると、出来上がった経路が条件を満たさなくなる。
        条件協議の結果が「期限を延ばす」だったとき、直せないと再依頼しても同じ結果になる */}
    {isSales && can(booking, "REVISE_SCHEDULE") && (
        <section className="space-y-2 rounded border border-gray-200 p-4">
          <h2 className="text-lg font-semibold text-gray-900">日程の訂正</h2>
          {revising ? (
            <form
              className="space-y-3"
              onSubmit={(event) => {
                event.preventDefault();
                const form = new FormData(event.currentTarget);
                const departureDate = textField(form, "departureDate");
                revise.mutate(
                  {
                    departureDate:
                      departureDate === "" ? null : departureDate,
                    arrivalDeadline: textField(form, "arrivalDeadline"),
                  },
                  { onSuccess: () => setRevising(false) },
                );
              }}
            >
              <div className="flex flex-wrap gap-4">
                <div>
                  <label
                    htmlFor="departureDate"
                    className="block text-sm font-medium text-gray-700"
                  >
                    出発希望日（任意）
                  </label>
                  <input
                    id="departureDate"
                    name="departureDate"
                    type="date"
                    defaultValue={booking.departureDate ?? ""}
                    className="rounded border border-gray-300 px-2 py-1"
                  />
                </div>
                <div>
                  <label
                    htmlFor="arrivalDeadline"
                    className="block text-sm font-medium text-gray-700"
                  >
                    到着期限
                  </label>
                  <input
                    id="arrivalDeadline"
                    name="arrivalDeadline"
                    type="date"
                    required
                    defaultValue={booking.arrivalDeadline}
                    className="rounded border border-gray-300 px-2 py-1"
                  />
                </div>
              </div>
              {revise.error !== null && revise.error !== undefined && (
                <p
                  role="alert"
                  className="rounded border border-red-200 bg-red-50 p-2 text-red-700"
                >
                  {revise.error instanceof ApiError
                    ? ((revise.error.body as { message?: string } | undefined)
                        ?.message ?? "日程を直せませんでした。")
                    : "日程を直せませんでした。時間をおいて再度お試しください。"}
                </p>
              )}
              <div className="flex gap-2">
                <button
                  type="submit"
                  disabled={revise.isPending}
                  className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
                >
                  日程を保存する
                </button>
                <button
                  type="button"
                  onClick={() => setRevising(false)}
                  className="rounded border border-gray-400 px-4 py-2 text-sm text-gray-700"
                >
                  やめる
                </button>
              </div>
            </form>
          ) : (
            <>
              <p className="text-sm text-gray-700">
                荷主と条件が変わったら、到着期限と出発希望日を直せます。
                {""}
                <strong>出発地・目的地・貨物の内容は直せません</strong>
                {""}
                （変えるならそれは別の予約です）。
              </p>
              <button
                type="button"
                onClick={() => setRevising(true)}
                className="rounded border border-gray-400 px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
              >
                日程を直す
              </button>
            </>
          )}
        </section>
      )}
    </>
  );
}

/**
 * 入力欄の値を文字列で取り出す。
 *
 * <p>`FormData` はファイルも返しうる。素朴に文字列化すると `[object Object]` がそのまま
 * 送られ、日付として読めない値が API に届く。
 */
function textField(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value : "";
}
