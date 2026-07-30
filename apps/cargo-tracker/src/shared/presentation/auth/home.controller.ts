import { Controller, Get, Inject, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { renderPage } from '../../../views/render.js';
import { Dashboard } from '../../../views/Dashboard.js';
import { Role } from '../../domain/model/role.js';
import { hasAnyRole } from '../../infrastructure/auth/authenticated-user.js';
import { DATABASE, type AppDatabase } from '../../infrastructure/database/database.js';

/**
 * ダッシュボード（/）。認証必須（グローバル AuthenticatedGuard・ADR-011）。全ロールが到達できる。
 * 経理担当者には未払い（OVERDUE）件数カードを表示するため、共有 DB の invoice を直読する
 * （ADR-008 の参照専用直読パターン。Billing のドメイン型は import しない = BC 独立性を保つ）。
 */
@Controller()
export class HomeController {
  constructor(@Inject(DATABASE) private readonly db: AppDatabase) {}

  @Get()
  async dashboard(@Req() req: Request, @Res() res: Response): Promise<void> {
    // AuthenticatedGuard 通過後は user が存在する
    const user = req.session.user!;
    const success = req.session.flash?.success;
    req.session.flash = {};
    const overdueInvoices = hasAnyRole(user, [Role.BILLING]) ? await this.countOverdueInvoices() : 0;
    renderPage(res, Dashboard({ user, success, overdueInvoices }));
  }

  /** 未払い（OVERDUE）の請求書件数を数える（経理担当者カード用の読みモデル） */
  private async countOverdueInvoices(): Promise<number> {
    const row = await this.db
      .selectFrom('invoice')
      .select((eb) => eb.fn.countAll<string>().as('count'))
      .where('paymentStatus', '=', 'OVERDUE')
      .executeTakeFirst();
    return row === undefined ? 0 : Number(row.count);
  }
}
