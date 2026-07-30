import { Body, Controller, Get, NotFoundException, Param, Post, Query, Req, Res, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { renderPage, renderFragment } from '../../../views/render.js';
import { TrackingIndex } from '../../../views/tracking/Index.js';
import { TrackingShow } from '../../../views/tracking/Show.js';
import { StatusTimeline } from '../../../views/tracking/StatusTimeline.js';
import { ExceptionNew } from '../../../views/tracking/ExceptionNew.js';
import { ExceptionIndex } from '../../../views/tracking/ExceptionIndex.js';
import { Role } from '../../../shared/domain/model/role.js';
import { RolesGuard } from '../../../shared/presentation/auth/roles.guard.js';
import { Roles } from '../../../shared/presentation/auth/roles.decorator.js';
import { TrackingValidationError } from '../domain/model/tracking-validation-error.js';
import {
  TrackCargoService,
  TrackingActivityNotFoundError,
} from '../application/commandservices/track-cargo.service.js';
import {
  ExceptionRegistrationForbiddenError,
  RegisterExceptionService,
} from '../application/commandservices/register-exception.service.js';
import { TrackingQueryService } from '../application/queryservices/tracking-query.service.js';

const VIEW_ROLES = [Role.SHIPPER, Role.SALES, Role.ROUTE_DESIGNER, Role.TRACKER] as const;

/**
 * 貨物追跡コントローラ（US17・追跡入力/詳細は US18 の先行導線）。
 * 追跡入力・詳細は荷主・営業・経路設計者・追跡管理者、手動更新は追跡管理者のみ。
 */
@Controller('tracking')
@UseGuards(RolesGuard)
export class TrackingController {
  constructor(
    private readonly trackCargo: TrackCargoService,
    private readonly registerException: RegisterExceptionService,
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

  @Get(':trackingNumber/exceptions/new')
  @Roles(Role.TRACKER, Role.HANDLER)
  async exceptionNew(@Param('trackingNumber') trackingNumber: string, @Req() req: Request, @Res() res: Response): Promise<void> {
    const detail = await this.queryService.findDetail(trackingNumber);
    if (detail === null) {
      throw new NotFoundException('追跡番号が見つかりません');
    }
    const flash = req.session.flash ?? {};
    req.session.flash = {};
    renderPage(res, ExceptionNew({ user: req.session.user!, trackingNumber, error: flash.error }));
  }

  @Post(':trackingNumber/exceptions')
  @Roles(Role.TRACKER, Role.HANDLER)
  async registerExceptionAction(
    @Param('trackingNumber') trackingNumber: string,
    @Body() body: Record<string, string>,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const isTracker = req.session.user!.roles.includes(Role.TRACKER);
    try {
      const added = await this.registerException.register(
        trackingNumber,
        {
          exceptionType: body.exceptionType ?? '',
          location: (body.location ?? '').trim().toUpperCase(),
          occurredAt: new Date(body.occurredAt ?? ''),
          description: body.description && body.description.trim().length > 0 ? body.description.trim() : null,
        },
        { isTracker },
      );
      req.session.flash = added
        ? { success: '例外を登録し、荷主へ通知しました' }
        : { warning: '同種の未解決例外が既に登録されているためスキップしました' };
      // 荷役作業員は例外一覧（TRACKER 専用）へ到達できないため、登録元の詳細/新規画面へ戻す
      res.redirect(isTracker ? `/tracking/${encodeURIComponent(trackingNumber)}/exceptions` : `/tracking/${encodeURIComponent(trackingNumber)}/exceptions/new`);
    } catch (error) {
      req.session.flash = { error: this.toMessage(error) };
      res.redirect(`/tracking/${encodeURIComponent(trackingNumber)}/exceptions/new`);
    }
  }

  @Get(':trackingNumber/exceptions')
  @Roles(Role.TRACKER)
  async exceptionList(@Param('trackingNumber') trackingNumber: string, @Req() req: Request, @Res() res: Response): Promise<void> {
    const exceptions = await this.queryService.findExceptions(trackingNumber);
    if (exceptions === null) {
      throw new NotFoundException('追跡番号が見つかりません');
    }
    const flash = req.session.flash ?? {};
    req.session.flash = {};
    renderPage(
      res,
      ExceptionIndex({
        user: req.session.user!,
        trackingNumber,
        exceptions,
        success: flash.success,
        warning: flash.warning,
        error: flash.error,
      }),
    );
  }

  @Post(':trackingNumber/exceptions/:id/report')
  @Roles(Role.TRACKER)
  async reportExceptionAction(
    @Param('trackingNumber') trackingNumber: string,
    @Param('id') id: string,
    @Body() body: Record<string, string>,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const listUrl = `/tracking/${encodeURIComponent(trackingNumber)}/exceptions`;
    if (!Number.isInteger(Number(id))) {
      req.session.flash = { error: '不正な例外 ID です' };
      res.redirect(listUrl);
      return;
    }
    try {
      await this.registerException.report(trackingNumber, Number(id), {
        newEstimatedArrival:
          body.newEstimatedArrival && body.newEstimatedArrival.trim().length > 0
            ? new Date(body.newEstimatedArrival)
            : null,
        notes: body.notes ?? '',
      });
      req.session.flash = { success: '対応報告を送信しました' };
    } catch (error) {
      req.session.flash = { error: this.toMessage(error) };
    }
    res.redirect(listUrl);
  }

  @Post(':trackingNumber/exceptions/:id/resolve')
  @Roles(Role.TRACKER)
  async resolveExceptionAction(
    @Param('trackingNumber') trackingNumber: string,
    @Param('id') id: string,
    @Body() body: Record<string, string>,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const listUrl = `/tracking/${encodeURIComponent(trackingNumber)}/exceptions`;
    if (!Number.isInteger(Number(id))) {
      req.session.flash = { error: '不正な例外 ID です' };
      res.redirect(listUrl);
      return;
    }
    try {
      await this.registerException.resolve(
        trackingNumber,
        Number(id),
        body.resolutionNotes && body.resolutionNotes.trim().length > 0 ? body.resolutionNotes.trim() : null,
      );
      req.session.flash = { success: '例外を解決しました' };
    } catch (error) {
      req.session.flash = { error: this.toMessage(error) };
    }
    res.redirect(listUrl);
  }

  private toMessage(error: unknown): string {
    if (
      error instanceof TrackingValidationError ||
      error instanceof TrackingActivityNotFoundError ||
      error instanceof ExceptionRegistrationForbiddenError
    ) {
      return error.message;
    }
    return '更新に失敗しました。入力内容を確認してください。';
  }
}
