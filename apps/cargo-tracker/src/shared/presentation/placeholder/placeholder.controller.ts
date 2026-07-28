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
  @Get('estimates')
  @Roles(Role.SALES)
  estimates(@Req() req: Request, @Res() res: Response): void {
    this.render(req, res, '見積管理', '/estimates', 'US01 輸送見積');
  }

  @Get('bookings')
  @Roles(Role.SALES, Role.SHIPPER, Role.ROUTE_DESIGNER)
  bookings(@Req() req: Request, @Res() res: Response): void {
    this.render(req, res, '貨物予約一覧', '/bookings', 'US04 貨物予約');
  }

  @Get('tracking')
  @Roles(Role.SALES, Role.SHIPPER, Role.ROUTE_DESIGNER, Role.TRACKER)
  tracking(@Req() req: Request, @Res() res: Response): void {
    this.render(req, res, '貨物追跡入力', '/tracking', 'US18 追跡照会');
  }

  @Get('handling')
  @Roles(Role.HANDLER, Role.TRACKER)
  handling(@Req() req: Request, @Res() res: Response): void {
    this.render(req, res, '荷役作業一覧', '/handling', 'US15 荷役作業');
  }

  @Get('voyages')
  @Roles(Role.ROUTE_DESIGNER)
  voyages(@Req() req: Request, @Res() res: Response): void {
    this.render(req, res, '航路一覧', '/voyages', 'US24 航海スケジュール');
  }

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
