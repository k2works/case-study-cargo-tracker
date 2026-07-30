import { Controller, Get, Req, Res, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { renderPage } from '../../../views/render.js';
import { Placeholder } from '../../../views/placeholder/Placeholder.js';
import { Role } from '../../domain/model/role.js';
import { AuthenticatedGuard } from '../auth/authenticated.guard.js';
import { RolesGuard } from '../auth/roles.guard.js';
import { Roles } from '../auth/roles.decorator.js';

/**
 * 未実装業務画面のプレースホルダ。ウォーキングスケルトンの全ルート到達性と
 * ロール別アクセス制御（ui_design ロール別到達性マトリクス）を成立させる。
 */
@Controller()
@UseGuards(AuthenticatedGuard, RolesGuard)
export class PlaceholderController {
  @Get('billing/invoices')
  @Roles(Role.BILLING)
  invoices(@Req() req: Request, @Res() res: Response): void {
    this.render(req, res, '請求書一覧', '/billing/invoices', 'US21 請求・精算');
  }

  private render(
    req: Request,
    res: Response,
    title: string,
    activePath: string,
    storyNote: string,
  ): void {
    renderPage(res, Placeholder({ user: req.session.user!, title, activePath, storyNote }));
  }
}
