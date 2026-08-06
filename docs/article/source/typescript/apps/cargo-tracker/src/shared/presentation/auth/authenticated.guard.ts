import {
  type CanActivate,
  type ExecutionContext,
  Injectable,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import type { Request, Response } from 'express';
import { IS_PUBLIC_KEY } from './public.decorator.js';

/**
 * 認証ガード（グローバル APP_GUARD・fail-closed。ADR-011）。
 * デフォルトで全ルートが認証を要求し、未認証の場合はログイン画面へリダイレクトする（US26）。
 * @Public() を付与したルート（ログイン・ヘルス・公開追跡 US18 等）のみ認証をスキップする。
 */
@Injectable()
export class AuthenticatedGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const isPublic = this.reflector.getAllAndOverride<boolean | undefined>(IS_PUBLIC_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (isPublic === true) {
      return true;
    }

    const req = context.switchToHttp().getRequest<Request>();
    if (req.session?.user) {
      return true;
    }
    const res = context.switchToHttp().getResponse<Response>();
    res.redirect('/login');
    return false;
  }
}
