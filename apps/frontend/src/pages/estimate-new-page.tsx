import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { RouteCandidateList } from "../features/booking/components/route-candidate-list";
import { HazardousFields } from "../features/booking/components/booking-form-fields";
import {
  EMPTY_HAZARDOUS,
  type HazardousInput,
} from "../features/booking/components/booking-form-types";
import {
  useCreateEstimate,
  useQuoteEstimate,
} from "../features/booking/estimate-queries";
import { useHazardClasses, useLocations } from "../features/booking/queries";
import { CARGO_TYPE_LABELS, type CargoType } from "../features/booking/types";

/**
 * 見積の作成（US01-1〜01-6）。
 *
 * <p><strong>候補を見てから作る。</strong>探した時点では保存しない——営業担当者は
 * 荷主と話しながら条件を変える。
 *
 * <p><strong>「候補が 0 件」と「間に合う候補が 0 件」を区別する</strong>（01-5）。
 * 後者は「最短でも N 日超過します」と出す——荷主に折り返す言葉が要る。
 */
export function EstimateNewPage() {
  const navigate = useNavigate();
  const { data: locations = [] } = useLocations();
  const hazardClasses = useHazardClasses();
  const quote = useQuoteEstimate();
  const create = useCreateEstimate();

  const [originUnLocode, setOriginUnLocode] = useState("");
  const [destinationUnLocode, setDestinationUnLocode] = useState("");
  const [arrivalDeadline, setArrivalDeadline] = useState("");
  const [cargoType, setCargoType] = useState<CargoType>("GENERAL");
  const [weightKg, setWeightKg] = useState("");
  // **危険物申告は予約と同じ項目を使う**（US05）。項目名が違うと、営業担当者は
  // 同じものを 2 度覚えることになる
  const [hazardous, setHazardous] = useState<HazardousInput>(EMPTY_HAZARDOUS);
  const [invalid, setInvalid] = useState<string | null>(null);
  /**
   * 保存した内容が、画面に出した候補と違ったとき。
   *
   * <p><strong>候補は保存のときに引き直す。</strong>概算料金は billingms が出すと
   * 決めており（[ADR-028] 決定 6）、画面が持っている数字をそのまま保存すると
   * <strong>そこが 2 つ目の式になる</strong>。引き直すぶん、探してから作るまでの
   * あいだに航海スケジュールが変われば内容が変わりうる——**変わったら黙らない**。
   */
  const [changed, setChanged] = useState<string | null>(null);

  const request = {
    originUnLocode,
    destinationUnLocode,
    arrivalDeadline,
    cargoType,
    weightKg: Number(weightKg),
  };

  function validate(): boolean {
    if (originUnLocode === "" || destinationUnLocode === "") {
      setInvalid("出発地と目的地を選んでください");
      return false;
    }
    if (originUnLocode === destinationUnLocode) {
      setInvalid("出発地と目的地が同じです");
      return false;
    }
    if (arrivalDeadline === "") {
      setInvalid("希望期限を入力してください");
      return false;
    }
    if (weightKg === "" || Number(weightKg) <= 0) {
      setInvalid("重量を入力してください");
      return false;
    }
    setInvalid(null);
    return true;
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">見積の作成</h1>

      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          if (validate()) {
            quote.mutate(request);
          }
        }}
      >
        <div className="flex gap-4">
          <div className="flex-1">
            <label className="block text-sm" htmlFor="originUnLocode">
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
            <label className="block text-sm" htmlFor="destinationUnLocode">
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
            <label className="block text-sm" htmlFor="arrivalDeadline">
              希望期限
            </label>
            <input
              id="arrivalDeadline"
              type="date"
              value={arrivalDeadline}
              onChange={(event) => setArrivalDeadline(event.target.value)}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            />
          </div>
          <div className="flex-1">
            <label className="block text-sm" htmlFor="cargoType">
              貨物種別
            </label>
            <select
              id="cargoType"
              value={cargoType}
              onChange={(event) => {
                const next = event.target.value as CargoType;
                setCargoType(next);
                // **種別を戻したら入力を捨てる**（US05 と同じ扱い）。残すと、
                // 画面に出ていない値が黙って送られる
                if (next !== "HAZARDOUS") {
                  setHazardous(EMPTY_HAZARDOUS);
                }
              }}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            >
              {Object.entries(CARGO_TYPE_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <div className="flex-1">
            <label className="block text-sm" htmlFor="weightKg">
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
        </div>

        {/* 危険物申告（受入基準 01-6）。**種別が危険物のときだけ出す** */}
        {cargoType === "HAZARDOUS" && (
          <HazardousFields
            value={hazardous}
            onChange={setHazardous}
            hazardClasses={hazardClasses.data ?? []}
          />
        )}

        {invalid !== null && (
          <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-800">
            {invalid}
          </p>
        )}
        {quote.error !== null && (
          <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-800">
            候補を探せませんでした。しばらくしてからお試しください。
          </p>
        )}

        <button type="submit" className="rounded bg-blue-600 px-4 py-2 text-white">
          候補を探す
        </button>
      </form>

      {quote.data !== undefined && (
        <div className="space-y-4">
          {/* **「間に合う候補が無い」と「候補が無い」を区別する**（01-5）。
              前者は荷主に折り返す言葉（何日超過するか）がある */}
          {quote.data.daysExceeded !== null && (
            <p
              role="alert"
              className="rounded border border-amber-300 bg-amber-50 p-3 text-sm"
              data-testid="deadline-exceeded"
            >
              <strong>希望期限に間に合うルートがありません。</strong>
              {`最短でも ${quote.data.daysExceeded} 日超過します。期限の見直しをご相談ください。`}
            </p>
          )}
          {quote.data.candidates.length === 0 && quote.data.daysExceeded === null && (
            <p
              role="alert"
              className="rounded border border-amber-300 bg-amber-50 p-3 text-sm"
              data-testid="no-candidates"
            >
              <strong>この区間のルートが見つかりません。</strong>
              {'航海スケジュールの登録状況を経路設計者にご確認ください。'}
            </p>
          )}

          <RouteCandidateList candidates={quote.data.candidates} />

          {/* **変わったら黙らない。**荷主に伝えた数字と保存した数字が違うまま
              先へ進むと、あとで「言った / 言わない」になる */}
          {changed !== null && (
            <p
              role="alert"
              className="rounded border border-amber-300 bg-amber-50 p-3 text-sm"
              data-testid="candidates-changed"
            >
              <strong>候補が変わりました。</strong>
              {'探したあとに航海スケジュールが変わった可能性があります。保存した内容をご確認のうえ、荷主にお伝えください。'}
              <Link
                className="ml-2 text-blue-700 underline"
                to={`/booking/estimates/${changed}`}
              >
                保存した見積を開く
              </Link>
            </p>
          )}

          {create.error !== null && (
            <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-800">
              見積を作成できませんでした。
            </p>
          )}

          {/* **候補が無くても見積は残せる。**「探したが無かった」ことも記録である */}
          <button
            type="button"
            className="rounded bg-blue-600 px-4 py-2 text-white"
            disabled={create.isPending}
            onClick={() =>
              create.mutate(request, {
                onSuccess: (created) => {
                  const shown = JSON.stringify(quote.data?.candidates ?? []);
                  const saved = JSON.stringify(created.candidates);
                  if (shown === saved) {
                    navigate(`/booking/estimates/${created.estimateId}`);
                    return;
                  }
                  setChanged(created.estimateId);
                },
              })
            }
          >
            見積を作成する
          </button>
        </div>
      )}

      <Link className="text-blue-700 underline" to="/booking/estimates">
        見積管理へ戻る
      </Link>
    </div>
  );
}
