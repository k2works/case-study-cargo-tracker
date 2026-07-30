import { Body, Controller, Get, NotFoundException, Param, Post, Req, Res, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { renderPage } from '../../../views/render.js';
import { Customs } from '../../../views/handling/Customs.js';
import { Role } from '../../../shared/domain/model/role.js';
import { RolesGuard } from '../../../shared/presentation/auth/roles.guard.js';
import { Roles } from '../../../shared/presentation/auth/roles.decorator.js';
import { HandlingValidationError } from '../domain/model/handling-validation-error.js';
import {
  CustomsDeclarationService,
  DeclarationNotFoundError,
  HandlingActivityNotFoundError,
} from '../application/commandservices/customs-declaration.service.js';
import { CustomsQueryService } from '../application/queryservices/customs-query.service.js';

/**
 * 通関ステータスコントローラ（/tracking/{trackingNumber}/customs・US16 前提条件）。
 * 対象ロールは追跡管理者・荷役作業員（ui_design）。追跡番号 → 貨物を特定し、
 * 通関申告の一覧・登録（審査中）・状態更新（PRG）を提供する。HELD 遷移は CUSTOMS_HOLD 例外を自動登録する。
 */
@Controller('tracking/:trackingNumber/customs')
@UseGuards(RolesGuard)
export class CustomsController {
  constructor(
    private readonly customsService: CustomsDeclarationService,
    private readonly customsQuery: CustomsQueryService,
  ) {}

  @Get()
  @Roles(Role.TRACKER, Role.HANDLER)
  async index(@Param('trackingNumber') trackingNumber: string, @Req() req: Request, @Res() res: Response): Promise<void> {
    const view = await this.customsQuery.findByTrackingNumber(trackingNumber);
    if (view === null) {
      throw new NotFoundException('追跡番号が見つかりません');
    }
    const flash = req.session.flash ?? {};
    req.session.flash = {};
    renderPage(
      res,
      Customs({
        user: req.session.user!,
        trackingNumber,
        declarations: view.declarations,
        canRegister: view.latestHandlingActivityId !== null,
        success: flash.success,
        warning: flash.warning,
        error: flash.error,
      }),
    );
  }

  @Post()
  @Roles(Role.TRACKER, Role.HANDLER)
  async register(
    @Param('trackingNumber') trackingNumber: string,
    @Body() body: Record<string, string>,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const base = `/tracking/${encodeURIComponent(trackingNumber)}/customs`;
    const view = await this.customsQuery.findByTrackingNumber(trackingNumber);
    if (view === null) {
      throw new NotFoundException('追跡番号が見つかりません');
    }
    if (view.latestHandlingActivityId === null) {
      req.session.flash = { error: '通関申告を紐付ける荷役作業がありません。先に荷役作業を登録してください。' };
      res.redirect(base);
      return;
    }
    try {
      await this.customsService.register({
        handlingActivityId: view.latestHandlingActivityId,
        declaredAt: new Date(),
        remarks: body.remarks && body.remarks.trim().length > 0 ? body.remarks.trim() : null,
      });
      req.session.flash = { success: '通関申告を登録しました（審査中）' };
    } catch (error) {
      req.session.flash = { error: this.toMessage(error) };
    }
    res.redirect(base);
  }

  @Post(':declarationNumber/status')
  @Roles(Role.TRACKER, Role.HANDLER)
  async updateStatus(
    @Param('trackingNumber') trackingNumber: string,
    @Param('declarationNumber') declarationNumber: string,
    @Body() body: Record<string, string>,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const base = `/tracking/${encodeURIComponent(trackingNumber)}/customs`;
    try {
      await this.customsService.updateStatus(declarationNumber, body.status ?? '');
      req.session.flash =
        body.status === 'HELD'
          ? {
              success: '通関状態を更新しました',
              warning: '留置に伴い CUSTOMS_HOLD 例外の登録を要求しました（反映後に例外一覧へ表示されます）',
            }
          : { success: '通関状態を更新しました' };
    } catch (error) {
      req.session.flash = { error: this.toMessage(error) };
    }
    res.redirect(base);
  }

  private toMessage(error: unknown): string {
    if (
      error instanceof HandlingValidationError ||
      error instanceof HandlingActivityNotFoundError ||
      error instanceof DeclarationNotFoundError
    ) {
      return error.message;
    }
    return '通関申告の処理に失敗しました。';
  }
}
