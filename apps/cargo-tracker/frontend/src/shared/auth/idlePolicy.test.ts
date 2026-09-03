import { describe, expect, it } from 'vitest';
import { idlePolicyFor, WARN_AFTER_MINUTES, EXPIRE_AFTER_MINUTES, HANDLER_EXPIRE_AFTER_MINUTES } from './idlePolicy';

describe('無操作タイムアウトの方針', () => {
  it('既定は 15 分で警告、20 分で破棄', () => {
    // non_functional.md「無操作 15 分で警告、20 分で破棄」。
    const policy = idlePolicyFor(['ROLE_SALES']);
    expect(policy.warnAfterMinutes).toBe(WARN_AFTER_MINUTES);
    expect(policy.expireAfterMinutes).toBe(EXPIRE_AFTER_MINUTES);
    expect(WARN_AFTER_MINUTES).toBe(15);
    expect(EXPIRE_AFTER_MINUTES).toBe(20);
  });

  it('荷役ロールは 60 分', () => {
    // 屋外で断続的に使う。20 分で切ると、記録の途中で毎回入り直すことになる。
    const policy = idlePolicyFor(['ROLE_HANDLER']);
    expect(policy.expireAfterMinutes).toBe(HANDLER_EXPIRE_AFTER_MINUTES);
    expect(HANDLER_EXPIRE_AFTER_MINUTES).toBe(60);
  });

  it('荷役を兼ねる利用者は長いほうに合わせる', () => {
    // 短いほうに合わせると、荷役の作業中に切れる。ロールを兼ねること自体は
    // 運用として認めているので、そのとき最も長い猶予を採る。
    expect(idlePolicyFor(['ROLE_SALES', 'ROLE_HANDLER']).expireAfterMinutes)
      .toBe(HANDLER_EXPIRE_AFTER_MINUTES);
  });

  it('警告は破棄より前に出す', () => {
    // 警告が破棄と同時か後だと、入力中の内容が消えることを知らせられない。
    for (const roles of [['ROLE_SALES'], ['ROLE_HANDLER']] as const) {
      const policy = idlePolicyFor(roles);
      expect(policy.warnAfterMinutes).toBeLessThan(policy.expireAfterMinutes);
    }
  });
});
