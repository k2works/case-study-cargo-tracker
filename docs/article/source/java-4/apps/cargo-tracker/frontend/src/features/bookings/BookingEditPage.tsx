import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type SubmitEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '@/shared/api/client';
import { display } from '@/features/shippers/api';
import { ALERT, BUTTON_PRIMARY, CARD, LINK, NOTICE, PAGE_TITLE } from '@/shared/ui/styles';
import { fetchBooking, updateBooking, type CargoType } from './api';
import { CargoFields, cargoFieldsPayload } from './CargoFields';
import { canUpdateSpecification } from './transitions';

/**
 * S24 予約修正（UC03・UC04 / US32）。
 *
 * <p>入力の誤りを直す画面。<b>経路条件の調整（US10）とは別物</b>で、あちらは経路設計者が
 * 候補を出し直すために条件を動かす。</p>
 *
 * <p>修正できるのは仮受付の予約だけ。<b>判定は集約と同じ述語を呼ぶ</b>
 * （{@link canUpdateSpecification}）。直接 URL を叩いても開けないようにするのは、
 * 押してから 409 で気づくのが遅いからで、守りそのものは集約と Gateway が担う。</p>
 *
 * <p>入力欄は登録（S21）と同じものを使う。写すと、片方にだけ項目を足したときに
 * 「登録では入れられるのに修正では消える」が生まれる。</p>
 */
export function BookingEditPage() {
  const { bookingId = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [cargoType, setCargoType] = useState<CargoType>('GENERAL');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { data, isPending, isError } = useQuery({
    queryKey: ['booking', bookingId],
    queryFn: () => fetchBooking(bookingId),
    refetchInterval: (query) => (query.state.data?.state === 'pending' ? 2000 : false),
  });

  const booking = data?.state === 'ready' ? data.value : null;

  useEffect(() => {
    if (booking) {
      setCargoType(booking.cargoType);
    }
  }, [booking]);

  async function onSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    const form = new FormData(event.currentTarget);
    try {
      await updateBooking(bookingId, cargoFieldsPayload(form, cargoType));
      await queryClient.invalidateQueries({ queryKey: ['booking', bookingId] });
      navigate(`/bookings/${encodeURIComponent(bookingId)}`);
    } catch (e) {
      // 断ったのは集約の判断であって画面の誤りではない。理由をそのまま見せる。
      setError(
        e instanceof ApiError ? e.body.message : '修正できませんでした。もう一度お試しください',
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section>
      <h1 className={PAGE_TITLE}>予約を修正する</h1>
      <p className="mt-2 text-sm">
        <Link to={`/bookings/${encodeURIComponent(bookingId)}`} className={LINK}>
          予約詳細に戻る
        </Link>
      </p>

      {isPending && <output className={`${NOTICE} mt-4`}>読み込み中…</output>}
      {isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          予約を取得できませんでした
        </p>
      )}
      {data?.state === 'pending' && <output className={`${NOTICE} mt-4`}>{data.message}</output>}

      {booking && !canUpdateSpecification(booking.bookingStatus) && (
        <p role="alert" className={`${ALERT} mt-4`}>
          経路設計へ引き渡したあとの予約は修正できません。経路条件の調整は経路設計者が行います
        </p>
      )}

      {booking && canUpdateSpecification(booking.bookingStatus) && (
        <form onSubmit={onSubmit} className={`${CARD} mt-4 space-y-4`}>
          {/* 荷主は変えられない（不変条件 1）。間違えたならそれは別の予約である。 */}
          <p className="text-sm text-gray-700">
            予約番号 {booking.bookingNumber} ／ 荷主 {display(booking.shipperName)}
          </p>

          <CargoFields
            cargoType={cargoType}
            onCargoTypeChange={setCargoType}
            defaults={{
              originUnLocode: booking.originUnLocode,
              destinationUnLocode: booking.destinationUnLocode,
              arrivalDeadline: booking.arrivalDeadline,
              weightKg: booking.weightKg,
              quantity: String(booking.quantity),
              lengthCm: booking.lengthCm ?? '',
              widthCm: booking.widthCm ?? '',
              heightCm: booking.heightCm ?? '',
              productName: booking.productName,
              hazardImoClass: booking.hazardImoClass ?? '',
              hazardUnNumber: booking.hazardUnNumber ?? '',
              temperatureMinC: booking.temperatureMinC ?? '',
              temperatureMaxC: booking.temperatureMaxC ?? '',
            }}
            // 受け付けたときの期限が今日より前でも、その予約は読めなければならない。
            // 「今日以降」で縛ると、期限の過ぎた予約の品名すら直せなくなる。
            minArrivalDeadline={booking.arrivalDeadline}
          />

          {error !== null && (
            <p role="alert" className={ALERT}>
              {error}
            </p>
          )}

          <button type="submit" aria-disabled={submitting} className={BUTTON_PRIMARY}>
            {submitting ? '送信中…' : '修正する'}
          </button>
        </form>
      )}
    </section>
  );
}
