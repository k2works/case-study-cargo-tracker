import { Body, Controller, Get, Post, Query, Req, Res, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { renderPage } from '../../../views/render.js';
import { HandlingIndex } from '../../../views/handling/Index.js';
import { HandlingNew } from '../../../views/handling/New.js';
import { Role } from '../../../shared/domain/model/role.js';
import { RolesGuard } from '../../../shared/presentation/auth/roles.guard.js';
import { Roles } from '../../../shared/presentation/auth/roles.decorator.js';
import { HandlingValidationError } from '../domain/model/handling-validation-error.js';
import { SharedValidationError } from '../../../shared/domain/model/shared-validation-error.js';
import {
  CustomsNotClearedError,
  RegisterHandlingActivityService,
  TrackingNumberNotFoundError,
} from '../application/commandservices/register-handling-activity.service.js';
import { HandlingHistoryQueryService } from '../application/queryservices/handling-history-query.service.js';
import { HANDLING_TYPE_LABELS } from '../domain/model/handling-type.js';

/**
 * 荷役作業コントローラ（US15/US16）。
 * 一覧は荷役作業員・追跡管理者、登録は荷役作業員（ui_design ロール別到達性）。
 */
@Controller('handling')
@UseGuards(RolesGuard)
export class HandlingController {
  constructor(
    private readonly registerService: RegisterHandlingActivityService,
    private readonly historyQuery: HandlingHistoryQueryService,
  ) {}

  @Get()
  @Roles(Role.HANDLER, Role.TRACKER)
  async index(@Query('bookingId') bookingId: string | undefined, @Req() req: Request, @Res() res: Response): Promise<void> {
    const activities = await this.historyQuery.list(bookingId);
    const flash = req.session.flash ?? {};
    req.session.flash = {};
    renderPage(
      res,
      HandlingIndex({
        user: req.session.user!,
        activities,
        bookingIdFilter: bookingId,
        success: flash.success,
        warning: flash.warning,
      }),
    );
  }

  @Get('new')
  @Roles(Role.HANDLER)
  newForm(@Req() req: Request, @Res() res: Response): void {
    renderPage(res, HandlingNew({ user: req.session.user! }));
  }

  @Post()
  @Roles(Role.HANDLER)
  async register(@Body() body: Record<string, string>, @Req() req: Request, @Res() res: Response): Promise<void> {
    try {
      const result = await this.registerService.register({
        trackingNumber: body.trackingNumber ?? '',
        type: body.eventType ?? '',
        location: body.location ?? '',
        completionTime: new Date(body.completionTime ?? ''),
        voyageNumber: body.voyageNumber,
        operatorName: req.session.user!.username,
        consigneeConfirmation: body.consigneeConfirmation,
      });
      const label = (HANDLING_TYPE_LABELS as Record<string, string>)[body.eventType] ?? body.eventType;
      if (result.duplicated) {
        req.session.flash = {
          warning: `同一の荷役作業（${label}・同一日時）が登録済みのためスキップしました`,
        };
      } else if (result.misrouted) {
        req.session.flash = {
          success: `荷役作業（${label}）を登録しました`,
          warning: '作業場所が予定ルートと異なります。経路状態を MISROUTED として記録します（反映後に一覧へ表示されます）。',
        };
      } else if (result.warning) {
        req.session.flash = {
          success: `荷役作業（${label}）を登録しました`,
          warning: '⚠ 作業場所が予定ルートと異なります。記録内容を確認してください。',
        };
      } else {
        req.session.flash = { success: `荷役作業（${label}）を登録しました。貨物状態を更新します。` };
      }
      res.redirect('/handling');
    } catch (error) {
      res.status(200);
      renderPage(
        res,
        HandlingNew({
          user: req.session.user!,
          error: this.toMessage(error),
          values: body,
        }),
      );
    }
  }

  private toMessage(error: unknown): string {
    if (
      error instanceof HandlingValidationError ||
      error instanceof SharedValidationError ||
      error instanceof TrackingNumberNotFoundError ||
      error instanceof CustomsNotClearedError
    ) {
      return error.message;
    }
    return '登録に失敗しました。入力内容を確認してください。';
  }
}
