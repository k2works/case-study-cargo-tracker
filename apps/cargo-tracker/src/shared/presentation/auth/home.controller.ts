import { Controller, Get, Req, Res, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { renderPage } from '../../../views/render.js';
import { Dashboard } from '../../../views/Dashboard.js';
import { AuthenticatedGuard } from './authenticated.guard.js';

/**
 * ダッシュボード（/）。認証必須。全ロールが到達できる。
 */
@Controller()
@UseGuards(AuthenticatedGuard)
export class HomeController {
  @Get()
  dashboard(@Req() req: Request, @Res() res: Response): void {
    // AuthenticatedGuard 通過後は user が存在する
    const user = req.session.user!;
    renderPage(res, Dashboard({ user }));
  }
}
