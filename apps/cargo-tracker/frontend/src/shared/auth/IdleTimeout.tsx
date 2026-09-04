import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuthStore } from './authStore';
import { idlePolicyFor } from './idlePolicy';

/** 数え直しの対象にする操作。スクロールだけの閲覧も「使っている」と見なす。 */
const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'scroll', 'visibilitychange'] as const;

/**
 * 無操作タイムアウト（non_functional.md「セッション」）。
 *
 * <p>共用端末に開きっぱなしの画面を残さない。警告を先に出すのは、入力中の内容が
 * 消えることを知らせるため。黙って切ると、書きかけの予約が消えた理由が分からない。</p>
 */
export function IdleTimeout() {
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);
  const [warned, setWarned] = useState(false);
  const warnTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const expireTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const roles = user?.roles;

  const schedule = useCallback(() => {
    if (!roles) {
      return;
    }
    const policy = idlePolicyFor(roles);
    if (warnTimer.current) {
      clearTimeout(warnTimer.current);
    }
    if (expireTimer.current) {
      clearTimeout(expireTimer.current);
    }
    setWarned(false);
    warnTimer.current = setTimeout(() => setWarned(true), policy.warnAfterMinutes * 60_000);
    expireTimer.current = setTimeout(() => {
      setWarned(false);
      logout();
    }, policy.expireAfterMinutes * 60_000);
  }, [roles, logout]);

  useEffect(() => {
    if (!roles) {
      return;
    }
    schedule();
    // 操作のたびに数え直す。消えるだけの実装にすると、警告が出たあと
    // 操作しても切れる時刻が変わらない。
    for (const event of ACTIVITY_EVENTS) {
      globalThis.addEventListener(event, schedule, { passive: true });
    }
    return () => {
      for (const event of ACTIVITY_EVENTS) {
        globalThis.removeEventListener(event, schedule);
      }
      if (warnTimer.current) {
        clearTimeout(warnTimer.current);
      }
      if (expireTimer.current) {
        clearTimeout(expireTimer.current);
      }
    };
  }, [roles, schedule]);

  if (!warned || !user) {
    return null;
  }

  const policy = idlePolicyFor(user.roles);
  const remaining = policy.expireAfterMinutes - policy.warnAfterMinutes;

  return (
    <div
      role="alert"
      className={
        'fixed inset-x-0 bottom-0 z-50 border-t border-amber-300 bg-amber-50 px-4 py-3'
        + ' text-sm text-amber-800'
      }
    >
      {`操作がないため、あと ${remaining} 分でログアウトします。`}
      <strong className="ml-1">入力中の内容は保存されません。</strong>
      {' 画面を操作すると延長されます。'}
    </div>
  );
}
