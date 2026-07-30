import { Body, Controller, Get, NotFoundException, Param, Post, Query, Req, Res, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { renderPage, renderFragment } from '../../../views/render.js';
import { TrackingIndex } from '../../../views/tracking/Index.js';
import { TrackingShow } from '../../../views/tracking/Show.js';
import { StatusTimeline } from '../../../views/tracking/StatusTimeline.js';
import { Role } from '../../../shared/domain/model/role.js';
import { AuthenticatedGuard } from '../../../shared/presentation/auth/authenticated.guard.js';
import { RolesGuard } from '../../../shared/presentation/auth/roles.guard.js';
import { Roles } from '../../../shared/presentation/auth/roles.decorator.js';
import { TrackingValidationError } from '../domain/model/tracking-validation-error.js';
import {
  TrackCargoService,
  TrackingActivityNotFoundError,
} from '../application/commandservices/track-cargo.service.js';
import { TrackingQueryService } from '../application/queryservices/tracking-query.service.js';

const VIEW_ROLES = [Role.SHIPPER, Role.SALES, Role.ROUTE_DESIGNER, Role.TRACKER] as const;

/**
 * 貨物追跡コントローラ（US17・追跡入力/詳細は US18 の先行導線）。
 * 追跡入力・詳細は荷主・営業・経路設計者・追跡管理者、手動更新は追跡管理者のみ。
 */
@Controller('tracking')
@UseGuards(AuthenticatedGuard, RolesGuard)
export class TrackingController {
  constructor(
    private readonly trackCargo: TrackCargoService,
    private readonly queryService: TrackingQueryService,
  ) {}

  @Get()
  @Roles(...VIEW_ROLES)
  async index(@Query('trackingNumber') trackingNumber: string | undefined, @Req() req: Request, @Res() res: Response): Promise<void> {
    if (trackingNumber !== undefined && trackingNumber.trim().length > 0) {
      const detail = await this.queryService.findDetail(trackingNumber.trim());
      if (detail !== null) {
        res.redirect(`/tracking/${encodeURIComponent(detail.trackingNumber)}`);
        return;
      }
      renderPage(
        res,
        TrackingIndex({ user: req.session.user!, error: `追跡番号が見つかりません: ${trackingNumber}` }),
      );
      return;
    }
    renderPage(res, TrackingIndex({ user: req.session.user! }));
  }

  @Get(':trackingNumber')
  @Roles(...VIEW_ROLES)
  async show(@Param('trackingNumber') trackingNumber: string, @Req() req: Request, @Res() res: Response): Promise<void> {
    const detail = await this.queryService.findDetail(trackingNumber);
    if (detail === null) {
      throw new NotFoundException('追跡番号が見つかりません');
    }
    const flash = req.session.flash ?? {};
    req.session.flash = {};
    renderPage(
      res,
      TrackingShow({
        user: req.session.user!,
        detail,
        success: flash.success,
        warning: flash.warning,
        error: flash.error,
      }),
    );
  }

  @Get(':trackingNumber/status')
  @Roles(...VIEW_ROLES)
  async status(@Param('trackingNumber') trackingNumber: string, @Res() res: Response): Promise<void> {
    const detail = await this.queryService.findDetail(trackingNumber);
    if (detail === null) {
      throw new NotFoundException('追跡番号が見つかりません');
    }
    renderFragment(
      res,
      StatusTimeline({ detail, fragmentPath: `/tracking/${encodeURIComponent(trackingNumber)}/status` }),
    );
  }

  @Post(':trackingNumber/events')
  @Roles(Role.TRACKER)
  async addManualEvent(
    @Param('trackingNumber') trackingNumber: string,
    @Body() body: Record<string, string>,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const base = `/tracking/${encodeURIComponent(trackingNumber)}`;
    try {
      const added = await this.trackCargo.addManualEvent(trackingNumber, {
        eventType: body.eventType ?? '',
        location: (body.location ?? '').trim().toUpperCase(),
        completionTime: new Date(body.completionTime ?? ''),
        voyageNumber: body.voyageNumber && body.voyageNumber.trim().length > 0 ? body.voyageNumber : null,
      });
      req.session.flash = added
        ? { success: '追跡情報を更新し、荷主へ状態変更を通知しました' }
        : { warning: '同一イベント（種別・日時）が記録済みのためスキップしました' };
    } catch (error) {
      req.session.flash = { error: this.toMessage(error) };
    }
    res.redirect(base);
  }

  private toMessage(error: unknown): string {
    if (error instanceof TrackingValidationError || error instanceof TrackingActivityNotFoundError) {
      return error.message;
    }
    return '更新に失敗しました。入力内容を確認してください。';
  }
}
