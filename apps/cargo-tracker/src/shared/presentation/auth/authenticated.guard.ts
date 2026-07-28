import {
  type CanActivate,
  type ExecutionContext,
  Injectable,
} from '@nestjs/common';
import type { Request, Response } from 'express';

/**
 * 認証ガード。未認証の場合はログイン画面へリダイレクトする（US26）。
 * 公開ルート（追跡照会 US18・ログイン・ヘルス）はこのガードを適用しない。
 */
@Injectable()
export class AuthenticatedGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const req = context.switchToHttp().getRequest<Request>();
    if (req.session?.user) {
      return true;
    }
    const res = context.switchToHttp().getResponse<Response>();
    res.redirect('/login');
    return false;
  }
}
