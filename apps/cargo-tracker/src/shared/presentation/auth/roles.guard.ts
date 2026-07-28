import {
  type CanActivate,
  type ExecutionContext,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import type { Request, Response } from 'express';
import type { Role } from '../../domain/model/role.js';
import { hasAnyRole } from '../../infrastructure/auth/authenticated-user.js';
import { ROLES_KEY } from './roles.decorator.js';

/**
 * 認可ガード。@Roles で指定したロールを持たない場合は 403 を返す。
 * 未認証の場合はログイン画面へ誘導する。
 */
@Injectable()
export class RolesGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const required = this.reflector.getAllAndOverride<Role[] | undefined>(ROLES_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (!required || required.length === 0) {
      return true;
    }

    const req = context.switchToHttp().getRequest<Request>();
    const user = req.session?.user;
    if (!user) {
      const res = context.switchToHttp().getResponse<Response>();
      res.redirect('/login');
      return false;
    }
    if (!hasAnyRole(user, required)) {
      throw new ForbiddenException('この操作を行う権限がありません');
    }
    return true;
  }
}
