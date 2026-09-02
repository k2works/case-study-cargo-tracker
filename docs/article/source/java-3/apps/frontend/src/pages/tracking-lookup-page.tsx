import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { TrackingEventsTable } from "../features/tracking/components/tracking-events-table";
import { usePublicTracking } from "../features/tracking/queries";
import { ApiError } from "../lib/api-client";
import { useAuthStore } from "../stores/auth-store";

/**
 * 公開の追跡照会（US18）。**認証不要**。
 *
 * 荷主・荷受人が追跡番号だけで開く、このシステムで唯一ログインを要さない業務画面である。
 *
 * **出すものは [ADR-024] 決定 5 が決めた項目だけ。** 予約番号・荷主名・作業者・航海番号・
 * 例外の詳細は出さない——認証が無い以上、追跡番号を手に入れた誰もが見る。荷役の作業者名や
 * 予定外だった事実は、荷主に伝えるものではなく社内の手がかりである。
 */
export function TrackingLookupPage() {
  const { trackingNumber } = useParams();
  const loggedIn = useAuthStore((state) => state.user) !== null;
  const navigate = useNavigate();
  const [input, setInput] = useState(trackingNumber ?? "");
  const { data, error, isLoading } = usePublicTracking(trackingNumber ?? null);

  function submit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = input.trim();
    if (trimmed === "") {
      return;
    }
    navigate(`/tracking/${encodeURIComponent(trimmed)}`);
  }

  /**
   * 見つからない案内は<strong>サーバの文言をそのまま出す</strong>。
   *
   * 画面が同じ文を持つと、サーバ・モック・画面で 3 つの写しができ、番号の形を
   * 案内に足しても画面だけが古いまま残る（実際にそうなっていた。IT9 返済枠 0.3）。
   */
  const notFound = error instanceof ApiError && error.status === 404;

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">貨物の追跡</h1>
        {/*
          **戻り先は、その人が来た場所にする。** ログイン済みの利用者はサイドバーの
          「貨物追跡」から来る——ポータル（未ログインの入口）へ送ると、もう一度
          ログインを求められたように見える
        */}
        <Link
          to={loggedIn ? "/dashboard" : "/"}
          className="text-blue-600 hover:underline"
        >
          {loggedIn ? "ダッシュボードに戻る" : "トップに戻る"}
        </Link>
      </div>

      {/* **行き止まりにしない。**見つからなくても、同じ画面で打ち直せる */}
      <form onSubmit={submit} className="flex gap-2">
        <label htmlFor="trackingNumber" className="sr-only">
          追跡番号
        </label>
        <input
          id="trackingNumber"
          type="text"
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder="TRK-20260819-1234"
          className="flex-1 rounded border border-gray-300 px-3 py-2"
        />
        <button
          type="submit"
          className="rounded bg-blue-600 px-4 py-2 text-white"
        >
          追跡する
        </button>
      </form>

      {isLoading && <p className="text-sm text-gray-600">照会しています…</p>}

      {notFound && (
        <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-800">
          {error.message}
        </p>
      )}

      {error !== null && error !== undefined && !notFound && (
        <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-800">
          {/*
            サーバが理由を返しているならそれを出す。上限に当たったとき
            （429「照会が多すぎます」）に「ただいま照会できません」と出すと、
            荷主は障害だと受け取って何度も押し、状況を悪くする。

            出すのは 429 のときだけにする。500 の本文には利用者に意味の無い
            文字列が入りうるため、それを画面に流さない。
          */}
          {error instanceof ApiError && error.status === 429
            ? error.message
            : "ただいま照会できません。しばらくしてからお試しください。"}
        </p>
      )}

      {data !== undefined && (
        <>
          <section className="space-y-2 rounded border border-gray-200 p-4">
            <h2 className="text-lg font-semibold text-gray-900">
              {data.trackingNumber}
            </h2>
            <dl className="grid gap-2 sm:grid-cols-3">
              <div>
                <dt className="text-sm text-gray-600">現在の状態</dt>
                <dd className="font-medium text-gray-900">
                  {data.statusLabel}
                </dd>
              </div>
              <div>
                <dt className="text-sm text-gray-600">現在地</dt>
                <dd className="font-medium text-gray-900">
                  {data.locationName}
                </dd>
              </div>
              <div>
                {/* **分からなければ「未定」。**0 や今日で埋めると「今日着く」と読まれる */}
                <dt className="text-sm text-gray-600">到着予定日</dt>
                <dd className="font-medium text-gray-900">
                  {data.estimatedArrival ?? "未定"}
                </dd>
              </div>
            </dl>
            {data.hasException && (
              <p
                role="alert"
                className="rounded bg-amber-50 p-3 text-sm text-amber-900"
              >
                <strong>お荷物に問題が起きています。</strong>
                {/*
                  **急かす言葉をやめ、次にすることを書く**（[ADR-025] 決定 2）。
                  「至急のご連絡が必要です」は、何をすればよいか伝えずに緊急だけを渡す。

                  緊急を隠さないのは、隠せるのが「荷主が何もしなくてよい」ときだけ
                  だからである。紛失は荷主が補償と再手配を判断する事象であり、
                  知らせないほうが害が大きい。**種別は書かない**（[ADR-024] 決定 3）。

                  改行を空白と読ませない（日本語は語間を空けない）
                */}
                {data.urgent && (
                  <strong>ご依頼元へのご連絡をおすすめします。</strong>
                )}
                詳しくはご依頼元の営業担当へお問い合わせください。
              </p>
            )}
          </section>

          <section className="space-y-2">
            <h2 className="text-lg font-semibold text-gray-900">
              これまでの経過
            </h2>
            <TrackingEventsTable events={data.events} />
          </section>

          <section className="space-y-2">
            <h2 className="text-lg font-semibold text-gray-900">お知らせ</h2>
            {/* **代替であることを書く**（[ADR-024] 決定 9）。書かないと、荷主は
                「メールが来ないのは不具合」と受け取る */}
            <p className="text-sm text-gray-600">
              {/* 改行を空白と読ませない（日本語は語間を空けない） */}
              状態が変わったときのお知らせは、
              <strong>この画面に出ます。メールは送っていません。</strong>
            </p>
            {data.notices.length === 0 ? (
              <p className="text-sm text-gray-600">お知らせはありません。</p>
            ) : (
              <ul className="space-y-1 text-sm text-gray-900">
                {data.notices.map((item) => (
                  <li key={`${item.noticedAt}-${item.message}`}>
                    {item.noticedAt}: {item.message}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </div>
  );
}
