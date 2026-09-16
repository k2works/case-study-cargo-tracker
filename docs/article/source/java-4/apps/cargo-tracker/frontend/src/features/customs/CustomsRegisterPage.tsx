import { useState, type SubmitEvent } from 'react';
import { useNavigate } from 'react-router';
import {
  ALERT,
  BUTTON_PRIMARY,
  BUTTON_SECONDARY,
  CARD,
  FIELD,
  LABEL,
  PAGE_TITLE,
} from '@/shared/ui/styles';
import { businessLocalToInstant } from '@/shared/api/businessDate';
import { ApiError } from '@/shared/api/client';
import { registerCustomsDeclaration } from './api';

/**
 * S53 通関申告の登録（UC21 / US29 §受入基準 1）。<b>荷役ロール</b>。
 *
 * <p><b>申告番号は利用者が入れる。</b> 採番するのは税関で、こちらでは作らない。
 * 書式も検査しない（不変条件 1）——国ごとに違うものを、こちらの想像で縛らない。
 * 空だけを断る。</p>
 *
 * <p><b>断られた理由はそのまま出す。</b> 「未決着の申告がある」は業務の話で、
 * 直し方（先にその申告の状態を更新する）が理由の中にある。</p>
 */
export function CustomsRegisterPage() {
  const navigate = useNavigate();
  const [declarationNumber, setDeclarationNumber] = useState('');
  const [trackingNumber, setTrackingNumber] = useState('');
  const [declaredAt, setDeclaredAt] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setSending(true);
    setError(null);
    try {
      await registerCustomsDeclaration({
        declarationNumber: declarationNumber.trim(),
        trackingNumber: trackingNumber.trim().toUpperCase(),
        // datetime-local は秒とタイムゾーンを持たない。**共有ヘルパで業務
        // タイムゾーンとして解く**（IT12 レビュー 高）。`new Date(...)` が使うのは
        // ブラウザの時間帯なので、海外港や UTC の端末から入れると申告日時が
        // 数時間ずれて記録され、しかもエラーは出ない。留置営業日数の起点に
        // 効くので、督促の判定が 1 日ずれる。
        declaredAt: businessLocalToInstant(declaredAt),
      });
      navigate(`/customs/${encodeURIComponent(declarationNumber.trim())}`);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '通関申告を登録できませんでした');
    } finally {
      setSending(false);
    }
  }

  return (
    <div>
      <h1 className={PAGE_TITLE}>通関申告の登録</h1>
      <p className="mt-1 text-sm text-gray-600">
        輸入港での通関申告を登録します。登録した時点の状態は「審査中」です。
      </p>

      {error && <p role="alert" className={`${ALERT} mt-4`}>{error}</p>}

      <form className={`${CARD} mt-4 grid gap-4`} onSubmit={submit}>
        <div>
          <label className={LABEL} htmlFor="declaration-number">申告番号</label>
          <input
            id="declaration-number"
            className={FIELD}
            required
            value={declarationNumber}
            onChange={(event) => setDeclarationNumber(event.target.value)}
          />
          <p className="mt-1 text-xs text-gray-600">
            税関が採番した番号をそのまま入れます。
          </p>
        </div>
        <div>
          <label className={LABEL} htmlFor="declaration-tracking-number">追跡番号</label>
          <input
            id="declaration-tracking-number"
            className={FIELD}
            required
            value={trackingNumber}
            onChange={(event) => setTrackingNumber(event.target.value)}
          />
        </div>
        <div>
          <label className={LABEL} htmlFor="declared-at">申告日時</label>
          <input
            id="declared-at"
            type="datetime-local"
            className={FIELD}
            required
            value={declaredAt}
            onChange={(event) => setDeclaredAt(event.target.value)}
          />
        </div>
        <div className="flex flex-wrap gap-3">
          <button type="submit" className={BUTTON_PRIMARY} disabled={sending}>
            {sending ? '送信中…' : '登録する'}
          </button>
          <button
            type="button"
            className={BUTTON_SECONDARY}
            onClick={() => navigate('/customs')}
          >
            一覧へ戻る
          </button>
        </div>
      </form>
    </div>
  );
}
