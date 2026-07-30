import { Controller, Get, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { renderPage } from '../../../views/render.js';
import { Dashboard } from '../../../views/Dashboard.js';

/**
 * ダッシュボード（/）。認証必須（グローバル AuthenticatedGuard・ADR-011）。全ロールが到達できる。
 */
@Controller()
export class HomeController {
  @Get()
  dashboard(@Req() req: Request, @Res() res: Response): void {
    // AuthenticatedGuard 通過後は user が存在する
    const user = req.session.user!;
    const success = req.session.flash?.success;
    req.session.flash = {};
    renderPage(res, Dashboard({ user, success }));
  }
}
