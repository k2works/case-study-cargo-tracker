import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Role } from '../../domain/model/role.js';
import { AuthenticationService } from './authentication.service.js';
import type {
  PasswordVerifier,
  UserAccount,
  UserRepository,
} from './user-account.js';

function makeUser(overrides: Partial<UserAccount> = {}): UserAccount {
  return {
    id: 1,
    username: 'sales1',
    passwordHash: 'HASH',
    roles: [Role.SALES],
    enabled: true,
    failedAttempts: 0,
    ...overrides,
  };
}

describe('AuthenticationService', () => {
  let repo: UserRepository;
  let verifier: PasswordVerifier;
  let service: AuthenticationService;

  beforeEach(() => {
    repo = {
      findByUsername: vi.fn(),
      incrementFailedAttempts: vi.fn().mockResolvedValue(undefined),
      resetFailedAttempts: vi.fn().mockResolvedValue(undefined),
    };
    verifier = { verify: vi.fn() };
    service = new AuthenticationService(repo, verifier);
  });

  it('資格情報が一致すれば SUCCESS を返し失敗回数をリセットする', async () => {
    vi.mocked(repo.findByUsername).mockResolvedValue(makeUser());
    vi.mocked(verifier.verify).mockResolvedValue(true);

    const result = await service.authenticate('sales1', 'correct');

    expect(result.outcome).toBe('SUCCESS');
    if (result.outcome === 'SUCCESS') {
      expect(result.user.username).toBe('sales1');
      expect(result.user.roles).toContain(Role.SALES);
    }
    expect(repo.resetFailedAttempts).toHaveBeenCalledWith(1);
  });

  it('存在しないユーザーは INVALID_CREDENTIALS を返す', async () => {
    vi.mocked(repo.findByUsername).mockResolvedValue(null);
    const result = await service.authenticate('ghost', 'x');
    expect(result.outcome).toBe('INVALID_CREDENTIALS');
  });

  it('パスワード不一致は INVALID_CREDENTIALS を返し失敗回数を加算する', async () => {
    vi.mocked(repo.findByUsername).mockResolvedValue(makeUser());
    vi.mocked(verifier.verify).mockResolvedValue(false);

    const result = await service.authenticate('sales1', 'wrong');

    expect(result.outcome).toBe('INVALID_CREDENTIALS');
    expect(repo.incrementFailedAttempts).toHaveBeenCalledWith(1);
  });

  it('5回目の失敗でアカウントがロックされ LOCKED を返す', async () => {
    vi.mocked(repo.findByUsername).mockResolvedValue(makeUser({ failedAttempts: 4 }));
    vi.mocked(verifier.verify).mockResolvedValue(false);

    const result = await service.authenticate('sales1', 'wrong');

    expect(result.outcome).toBe('LOCKED');
    expect(repo.incrementFailedAttempts).toHaveBeenCalledWith(1);
  });

  it('既にロック済み（失敗5回以上）なら照合せず LOCKED を返す', async () => {
    vi.mocked(repo.findByUsername).mockResolvedValue(makeUser({ failedAttempts: 5 }));

    const result = await service.authenticate('sales1', 'correct');

    expect(result.outcome).toBe('LOCKED');
    expect(verifier.verify).not.toHaveBeenCalled();
  });

  it('無効化アカウントは DISABLED を返す', async () => {
    vi.mocked(repo.findByUsername).mockResolvedValue(makeUser({ enabled: false }));

    const result = await service.authenticate('sales1', 'correct');

    expect(result.outcome).toBe('DISABLED');
    expect(verifier.verify).not.toHaveBeenCalled();
  });
});
