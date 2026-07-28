import { describe, expect, it } from 'vitest';
import { Role } from '../../domain/model/role.js';
import { hasAnyRole, type AuthenticatedUser } from './authenticated-user.js';

const user = (roles: Role[]): AuthenticatedUser => ({ id: 1, username: 'u', roles });

describe('hasAnyRole', () => {
  it('必要ロールのいずれかを持てば true', () => {
    expect(hasAnyRole(user([Role.SALES]), [Role.SALES, Role.SHIPPER])).toBe(true);
  });

  it('必要ロールを持たなければ false', () => {
    expect(hasAnyRole(user([Role.BILLING]), [Role.SALES])).toBe(false);
  });

  it('ロールを持たないユーザーは false', () => {
    expect(hasAnyRole(user([]), [Role.SALES])).toBe(false);
  });
});
