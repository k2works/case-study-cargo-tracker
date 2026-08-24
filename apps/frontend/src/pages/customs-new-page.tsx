import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useRegisterCustomsDeclaration } from "../features/customs/queries";
import { ApiError } from "../lib/api-client";

/**
 * 通関申告の登録（US29-1）。
 *
 * **荷役作業員が使う。** 追跡番号を起点に作業しており、予約番号は知らない
 * （[ADR-023] 決定 2 と同じ立場）。
 *
 * **初期状態は画面が選ばない。** サーバが `PENDING` を決める。選ばせると、
 * 登録の時点で「通関済」を選べてしまい、引取のガードが最初から素通りになる。
 */
export function CustomsNewPage() {
  const navigate = useNavigate();
  const register = useRegisterCustomsDeclaration();

  const [trackingNumber, setTrackingNumber] = useState("");
  const [declarationNumber, setDeclarationNumber] = useState("");
  const [declaredAt, setDeclaredAt] = useState("");
  const [remarks, setRemarks] = useState("");
  const [registered, setRegistered] = useState<string | null>(null);

  function submit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    setRegistered(null);
    register.mutate(
      {
        trackingNumber,
        declarationNumber,
        // datetime-local は秒とタイムゾーンを持たない。サーバは ISO 8601 を受け取る
        declaredAt: new Date(declaredAt).toISOString(),
        remarks: remarks === "" ? null : remarks,
      },
      {
        onSuccess: (declaration) => {
          setRegistered(declaration.declarationNumber);
          setDeclarationNumber("");
          setRemarks("");
        },
      },
    );
  }

  /** サーバが返した理由をそのまま出す。画面が写しを持つと、片方だけ古くなる。 */
  const failure =
    register.error instanceof ApiError ? register.error.message : null;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">通関申告の登録</h1>
        <Link to="/customs" className="text-blue-600 hover:underline">
          通関申告一覧に戻る
        </Link>
      </div>

      {registered !== null && (
        <p
          role="status"
          className="rounded border border-green-300 bg-green-50 px-3 py-2 text-sm text-green-900"
        >
          申告 {registered} を登録しました。状態は「審査中」です。
          <button
            type="button"
            onClick={() => void navigate("/customs")}
            className="ml-2 text-blue-700 underline"
          >
            一覧で確認する
          </button>
        </p>
      )}

      {failure !== null && (
        <p
          role="alert"
          className="rounded border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-900"
        >
          {failure}
        </p>
      )}

      <form onSubmit={submit} className="max-w-xl space-y-4">
        <div>
          <label
            htmlFor="trackingNumber"
            className="block text-sm font-medium text-gray-700"
          >
            追跡番号
          </label>
          <input
            id="trackingNumber"
            required
            value={trackingNumber}
            onChange={(event) => setTrackingNumber(event.target.value)}
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          />
        </div>
        <div>
          <label
            htmlFor="declarationNumber"
            className="block text-sm font-medium text-gray-700"
          >
            申告番号
          </label>
          <input
            id="declarationNumber"
            required
            value={declarationNumber}
            onChange={(event) => setDeclarationNumber(event.target.value)}
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          />
        </div>
        <div>
          <label
            htmlFor="declaredAt"
            className="block text-sm font-medium text-gray-700"
          >
            申告日時
          </label>
          <input
            id="declaredAt"
            type="datetime-local"
            required
            value={declaredAt}
            onChange={(event) => setDeclaredAt(event.target.value)}
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          />
        </div>
        <div>
          <label
            htmlFor="remarks"
            className="block text-sm font-medium text-gray-700"
          >
            備考
          </label>
          <input
            id="remarks"
            value={remarks}
            onChange={(event) => setRemarks(event.target.value)}
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
          />
        </div>

        <p className="text-sm text-gray-600">
          登録した申告は<strong>「審査中」</strong>から始まります。通関済・留置・不可への
          更新は追跡管理者が行います。
        </p>

        <button
          type="submit"
          disabled={register.isPending}
          className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
        >
          登録する
        </button>
      </form>
    </div>
  );
}
