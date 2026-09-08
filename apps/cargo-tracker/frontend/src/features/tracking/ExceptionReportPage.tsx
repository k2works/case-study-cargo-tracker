import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import {
  ALERT,
  BUTTON_PRIMARY,
  CARD,
  FIELD,
  LABEL,
  LINK,
  PAGE_TITLE,
} from '@/shared/ui/styles';
import { ApiError } from '@/shared/api/client';
import {
  EXCEPTION_TYPE_LABELS,
  REPORTABLE_EXCEPTION_TYPES,
  registerException,
  type ExceptionType,
} from './api';

/**
 * 例外の起票（S43 / UC16・US19 §受入基準 1）。**追跡管理者だけ**。
 *
 * <p><b>自動で起票される種別は出さない。</b> 誤配は荷役が（US28）、税関保留は
 * 通関が（UC21）決める——手で選べるようにすると、起きていない誤配を記録できる。</p>
 *
 * <p><b>起票したらその追跡へ戻す。</b> 起票は目的ではなく、対応の入口である。</p>
 */
export function ExceptionReportPage() {
  const { trackingNumber = '' } = useParams();
  const navigate = useNavigate();

  const [exceptionType, setExceptionType] = useState<ExceptionType>('DELAY');
  const [unLocode, setUnLocode] = useState('');
  const [occurredAt, setOccurredAt] = useState('');
  const [description, setDescription] = useState('');
  // **鍵は画面が作る。** 応答が返らずもう一度押したとき、作り直すと別の鍵になり
  // 同じ例外が二重に起票される。
  const [exceptionId] = useState(() => crypto.randomUUID());

  const report = useMutation({
    mutationFn: () => registerException(trackingNumber, {
      exceptionId,
      exceptionType,
      unLocode: unLocode.trim().toUpperCase(),
      description: description.trim(),
      occurredAt,
    }),
    onSuccess: () => navigate(`/tracking/${trackingNumber}`),
  });

  return (
    <div>
      <h1 className={PAGE_TITLE}>例外を起票する</h1>
      <p className="mt-1 text-sm text-gray-600">追跡番号 {trackingNumber}</p>

      <section className={`${CARD} mt-4`}>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="exceptionType" className={LABEL}>
              例外種別
            </label>
            <select
              id="exceptionType"
              className={FIELD}
              value={exceptionType}
              onChange={(event) => setExceptionType(event.target.value as ExceptionType)}
            >
              {REPORTABLE_EXCEPTION_TYPES.map((type) => (
                <option key={type} value={type}>
                  {EXCEPTION_TYPE_LABELS[type]}
                </option>
              ))}
            </select>
            {/* 誤配と税関保留を出さない理由を隠さない。 */}
            <p className="mt-1 text-xs text-gray-600">
              誤配と税関保留は、荷役と通関の記録からシステムが起票します。
            </p>
          </div>
          <div>
            <label htmlFor="unLocode" className={LABEL}>
              発生場所
            </label>
            <input
              id="unLocode"
              className={FIELD}
              value={unLocode}
              onChange={(event) => setUnLocode(event.target.value.toUpperCase())}
              placeholder="SGSIN"
              autoComplete="off"
            />
            {/* 船の上で起きた破損は場所が分からない。空を許す。 */}
            <p className="mt-1 text-xs text-gray-600">
              分からなければ空のままで構いません（船の上で起きたときなど）。
            </p>
          </div>
          <div>
            <label htmlFor="occurredAt" className={LABEL}>
              発生日時
            </label>
            <input
              id="occurredAt"
              type="datetime-local"
              className={FIELD}
              value={occurredAt}
              onChange={(event) => setOccurredAt(event.target.value)}
            />
            <p className="mt-1 text-xs text-gray-600">
              空のままなら「いま」で記録します。
            </p>
          </div>
        </div>

        <div className="mt-4">
          <label htmlFor="description" className={LABEL}>
            発生状況
          </label>
          <textarea
            id="description"
            className={FIELD}
            rows={3}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            placeholder="台風で 3 日遅れます"
          />
          {/* **何が起きたか読めない記録を残さない。** 起票だけあって理由が無いと、
              対応する人は電話で聞き直すところから始める。 */}
          <p className="mt-1 text-xs text-gray-600">
            対応する人が読んで動けるように、起きたことを書いてください。
          </p>
        </div>

        <div className="mt-4 flex items-center gap-3">
          <button
            type="button"
            className={BUTTON_PRIMARY}
            disabled={description.trim() === '' || report.isPending}
            onClick={() => report.mutate()}
          >
            起票する
          </button>
          <button
            type="button"
            className={LINK}
            onClick={() => navigate(`/tracking/${trackingNumber}`)}
          >
            やめる
          </button>
        </div>

        {report.isError && (
          <output className={`${ALERT} mt-4`}>
            {report.error instanceof ApiError
              ? report.error.body.message
              : '起票できませんでした。もう一度お試しください。'}
          </output>
        )}
      </section>
    </div>
  );
}
