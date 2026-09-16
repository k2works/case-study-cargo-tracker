import type { Role } from './roles';

/**
 * 無操作タイムアウトの方針（non_functional.md「セッション」）。
 *
 * <p>共用端末に開きっぱなしの画面を残さない。荷役ロールだけ長いのは、屋外で
 * 断続的に使うため。20 分で切ると、記録の途中で毎回入り直すことになる。</p>
 */
export const WARN_AFTER_MINUTES = 15;
export const EXPIRE_AFTER_MINUTES = 20;
export const HANDLER_EXPIRE_AFTER_MINUTES = 60;
export const HANDLER_WARN_AFTER_MINUTES = 55;

export interface IdlePolicy {
  readonly warnAfterMinutes: number;
  readonly expireAfterMinutes: number;
}

/**
 * ロールに応じた猶予。
 *
 * <p>荷役を兼ねる利用者は<b>長いほうに合わせる</b>。短いほうに合わせると、
 * 荷役の作業中に切れる。ロールを兼ねること自体は運用として認めている。</p>
 */
export function idlePolicyFor(roles: readonly Role[]): IdlePolicy {
  if (roles.includes('ROLE_HANDLER')) {
    return {
      warnAfterMinutes: HANDLER_WARN_AFTER_MINUTES,
      expireAfterMinutes: HANDLER_EXPIRE_AFTER_MINUTES,
    };
  }
  return { warnAfterMinutes: WARN_AFTER_MINUTES, expireAfterMinutes: EXPIRE_AFTER_MINUTES };
}
