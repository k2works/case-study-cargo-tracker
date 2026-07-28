import { describe, expect, it, vi } from 'vitest';
import { ForbiddenException } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import type { ExecutionContext } from '@nestjs/common';
import { Role } from '../../domain/model/role.js';
import { RolesGuard } from './roles.guard.js';

function contextWith(user: unknown, redirect = vi.fn()): ExecutionContext {
  const req = { session: { user } };
  const res = { redirect };
  return {
    switchToHttp: () => ({ getRequest: () => req, getResponse: () => res }),
    getHandler: () => ({}),
    getClass: () => ({}),
  } as unknown as ExecutionContext;
}

function guardWith(required: Role[] | undefined): RolesGuard {
  const reflector = { getAllAndOverride: vi.fn().mockReturnValue(required) } as unknown as Reflector;
  return new RolesGuard(reflector);
}

describe('RolesGuard', () => {
  it('必要ロール未指定なら常に許可する', () => {
    expect(guardWith(undefined).canActivate(contextWith(undefined))).toBe(true);
  });

  it('必要ロールを持つユーザーを許可する', () => {
    const ctx = contextWith({ id: 1, username: 'u', roles: [Role.SALES] });
    expect(guardWith([Role.SALES]).canActivate(ctx)).toBe(true);
  });

  it('未認証はログインへリダイレクトし false を返す', () => {
    const redirect = vi.fn();
    const ctx = contextWith(undefined, redirect);
    expect(guardWith([Role.SALES]).canActivate(ctx)).toBe(false);
    expect(redirect).toHaveBeenCalledWith('/login');
  });

  it('権限不足は ForbiddenException を送出する', () => {
    const ctx = contextWith({ id: 1, username: 'u', roles: [Role.BILLING] });
    expect(() => guardWith([Role.SALES]).canActivate(ctx)).toThrow(ForbiddenException);
  });
});
