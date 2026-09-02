import type { LocationOption, MovementInput } from '../types'

/**
 * 航海の寄港地（区間の並び）の入力欄。
 *
 * <p>登録画面から切り出したのは、割る基準（1 ファイル 400 行）を超えたからだけではない。
 * <strong>ここだけが「区間を足したり消したりする」入力</strong>であり、航海の属性
 * （番号・船名・運送会社）とは変わる理由が違う。
 *
 * <p>検証はここでは行わない。区間の繋がりは<strong>並び全体</strong>を見ないと判断できず、
 * 入力欄ごとに判断すると「前の区間の到着地から出発しているか」を見られない
 * （{@link voyageInvalidMessage} が並びをまとめて見る）。
 */
export function MovementsFieldset({
  movements,
  locations,
  onChange,
  onAdd,
  onRemove,
}: Readonly<{
  movements: MovementInput[]
  locations: LocationOption[]
  onChange: (index: number, next: MovementInput) => void
  onAdd: () => void
  onRemove: (index: number) => void
}>) {
  return (
      <fieldset className="space-y-4 rounded border border-gray-200 p-4">
        <legend className="px-1 text-sm font-medium text-gray-700">寄港地（順番に入力）</legend>
        {movements.map((movement, index) => (
          <div
            key={movement.key}
            className="grid gap-3 rounded border border-gray-100 bg-gray-50 p-3 md:grid-cols-5"
          >
            <div>
              <label
                htmlFor={`departureUnLocode-${index}`}
                className="block text-sm font-medium text-gray-700"
              >
                {index + 1} 区間目の出発地
              </label>
              <select
                id={`departureUnLocode-${index}`}
                value={movement.departureUnLocode}
                onChange={(event) =>
                  onChange(index, {
                    ...movement,
                    departureUnLocode: event.target.value,
                  })
                }
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
              <label
                htmlFor={`arrivalUnLocode-${index}`}
                className="block text-sm font-medium text-gray-700"
              >
                {index + 1} 区間目の到着地
              </label>
              <select
                id={`arrivalUnLocode-${index}`}
                value={movement.arrivalUnLocode}
                onChange={(event) =>
                  onChange(index, {
                    ...movement,
                    arrivalUnLocode: event.target.value,
                  })
                }
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
              <label
                htmlFor={`departureTime-${index}`}
                className="block text-sm font-medium text-gray-700"
              >
                {index + 1} 区間目の出発日時
              </label>
              <input
                id={`departureTime-${index}`}
                type="datetime-local"
                value={movement.departureTime}
                onChange={(event) =>
                  onChange(index, {
                    ...movement,
                    departureTime: event.target.value,
                  })
                }
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
            </div>

            <div>
              <label
                htmlFor={`arrivalTime-${index}`}
                className="block text-sm font-medium text-gray-700"
              >
                {index + 1} 区間目の到着日時
              </label>
              <input
                id={`arrivalTime-${index}`}
                type="datetime-local"
                value={movement.arrivalTime}
                onChange={(event) =>
                  onChange(index, {
                    ...movement,
                    arrivalTime: event.target.value,
                  })
                }
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
            </div>

            <div className="flex items-end">
              {movements.length > 1 && (
                <button
                  type="button"
                  onClick={() => onRemove(index)}
                  className="rounded border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 hover:bg-gray-100"
                >
                  この区間を削除
                </button>
              )}
            </div>
          </div>
        ))}

        <button
          type="button"
          onClick={onAdd}
          className="rounded border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
        >
          寄港地を追加する
        </button>
      </fieldset>
  )
}
