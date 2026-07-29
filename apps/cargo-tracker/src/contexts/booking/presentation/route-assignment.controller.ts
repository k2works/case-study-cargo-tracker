import {
  Body,
  Controller,
  Get,
  Inject,
  Logger,
  NotFoundException,
  Param,
  Post,
  Query,
  Req,
  Res,
  UseGuards,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import { renderPage } from '../../../views/render.js';
import { RouteAssignment } from '../../../views/booking/RouteAssignment.js';
import { Role } from '../../../shared/domain/model/role.js';
import { isCargoType } from '../../../shared/domain/model/cargo-type.js';
import { AuthenticatedGuard } from '../../../shared/presentation/auth/authenticated.guard.js';
import { RolesGuard } from '../../../shared/presentation/auth/roles.guard.js';
import { Roles } from '../../../shared/presentation/auth/roles.decorator.js';
import { BookingValidationError } from '../domain/model/booking-validation-error.js';
import { BookingQueryService } from '../application/queryservices/booking-query.service.js';
import { RouteCargoService } from '../application/commandservices/route-cargo.service.js';
import { ConfirmBookingService } from '../application/commandservices/confirm-booking.service.js';
import { AssignTrackingNumberService } from '../application/commandservices/assign-tracking-number.service.js';
import {
  NotificationType,
  type NotificationPort,
} from '../application/outboundservices/acl/notification-port.js';
import type { RouteCandidateAcl } from '../application/outboundservices/acl/route-candidate-acl.js';
import { ROUTE_CANDIDATE_ACL, NOTIFICATION_PORT } from '../booking.tokens.js';

/**
 * 経路確定・通知・予約確定・追跡番号発行コントローラ（US09〜US14）。
 * 経路割り当て・追跡番号発行は経路設計者、通知・確定・差戻し・キャンセルは営業担当者。
 */
@Controller('bookings')
@UseGuards(AuthenticatedGuard, RolesGuard)
export class RouteAssignmentController {
  private readonly logger = new Logger(RouteAssignmentController.name);

  constructor(
    private readonly queryService: BookingQueryService,
    private readonly routeCargoService: RouteCargoService,
    private readonly confirmService: ConfirmBookingService,
    private readonly trackingService: AssignTrackingNumberService,
    @Inject(ROUTE_CANDIDATE_ACL) private readonly candidates: RouteCandidateAcl,
    @Inject(NOTIFICATION_PORT) private readonly notifier: NotificationPort,
  ) {}

  @Get(':bookingId/route')
  @Roles(Role.ROUTE_DESIGNER)
  async routePage(
    @Param('bookingId') bookingId: string,
    @Query('arrivalDeadline') deadlineParam: string | undefined,
    @Query('cargoType') cargoTypeParam: string | undefined,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const booking = await this.queryService.findDetail(bookingId);
    if (booking === null) {
      throw new NotFoundException('予約が見つかりません');
    }
    const arrivalDeadline = deadlineParam && deadlineParam.length > 0 ? deadlineParam : booking.arrivalDeadlineText;
    const cargoType = isCargoType(cargoTypeParam ?? '') ? cargoTypeParam! : booking.cargoType;
    const candidates = await this.candidates.findCandidates({
      origin: booking.origin,
      destination: booking.destination,
      arrivalDeadline: new Date(arrivalDeadline),
      cargoType,
    });
    renderPage(
      res,
      RouteAssignment({
        user: req.session.user!,
        bookingId,
        origin: booking.origin,
        destination: booking.destination,
        arrivalDeadline,
        cargoType,
        candidates,
      }),
    );
  }

  @Post(':bookingId/route')
  @Roles(Role.ROUTE_DESIGNER)
  async assignRoute(
    @Param('bookingId') bookingId: string,
    @Body() body: Record<string, string>,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const booking = await this.queryService.findDetail(bookingId);
    if (booking === null) {
      throw new NotFoundException('予約が見つかりません');
    }
    const arrivalDeadline = body.arrivalDeadline && body.arrivalDeadline.length > 0 ? body.arrivalDeadline : booking.arrivalDeadlineText;
    const cargoType = isCargoType(body.cargoType ?? '') ? body.cargoType : booking.cargoType;
    const options = await this.candidates.findCandidates({
      origin: booking.origin,
      destination: booking.destination,
      arrivalDeadline: new Date(arrivalDeadline),
      cargoType,
    });
    const selected = options.find((option) => option.id === body.candidateId);
    if (selected === undefined) {
      req.session.flash = { error: '選択した経路候補が見つかりません。再度候補を選択してください。' };
      res.redirect(`${this.base(bookingId)}/route`);
      return;
    }
    try {
      await this.routeCargoService.route({ bookingId, legs: selected.legs });
      req.session.flash = { success: '経路を予約に紐付けました（経路提案中）' };
      res.redirect(this.base(bookingId));
    } catch (error) {
      req.session.flash = { error: this.toMessage(error) };
      res.redirect(`${this.base(bookingId)}/route`);
    }
  }

  @Post(':bookingId/notify')
  @Roles(Role.SALES)
  async notify(@Param('bookingId') bookingId: string, @Req() req: Request, @Res() res: Response): Promise<void> {
    const booking = await this.queryService.findDetail(bookingId);
    if (booking === null) {
      throw new NotFoundException('予約が見つかりません');
    }
    await this.notifier.notify({
      bookingId,
      notificationType: NotificationType.ROUTE_PROPOSED,
      recipient: booking.consigneeEmail ?? 'unknown@example.com',
    });
    req.session.flash = { success: '荷主に経路を通知しました（通知記録を登録）' };
    res.redirect(this.base(bookingId));
  }

  @Post(':bookingId/confirm')
  @Roles(Role.SALES)
  async confirm(@Param('bookingId') bookingId: string, @Req() req: Request, @Res() res: Response): Promise<void> {
    await this.run(() => this.confirmService.confirm(bookingId), '予約を確定しました（確定）', bookingId, req, res);
  }

  @Post(':bookingId/return-to-routing')
  @Roles(Role.SALES)
  async returnToRouting(@Param('bookingId') bookingId: string, @Req() req: Request, @Res() res: Response): Promise<void> {
    await this.run(() => this.confirmService.returnToRouting(bookingId), '経路設計に差し戻しました（経路設計中）', bookingId, req, res);
  }

  @Post(':bookingId/cancel')
  @Roles(Role.SALES)
  async cancel(@Param('bookingId') bookingId: string, @Req() req: Request, @Res() res: Response): Promise<void> {
    await this.run(() => this.confirmService.cancel(bookingId), '予約をキャンセルしました（荷主へ通知）', bookingId, req, res);
  }

  @Post(':bookingId/tracking-number')
  @Roles(Role.ROUTE_DESIGNER)
  async issueTracking(@Param('bookingId') bookingId: string, @Req() req: Request, @Res() res: Response): Promise<void> {
    try {
      const trackingNumber = await this.trackingService.issue(bookingId);
      req.session.flash = { success: `追跡番号を発行しました（${trackingNumber}）` };
    } catch (error) {
      req.session.flash = { error: this.toMessage(error) };
    }
    res.redirect(this.base(bookingId));
  }

  private async run(
    action: () => Promise<void>,
    successMessage: string,
    bookingId: string,
    req: Request,
    res: Response,
  ): Promise<void> {
    try {
      await action();
      req.session.flash = { success: successMessage };
    } catch (error) {
      req.session.flash = { error: this.toMessage(error) };
    }
    res.redirect(this.base(bookingId));
  }

  private base(bookingId: string): string {
    // パスパラメータ由来の値はエンコードして open redirect を防ぐ
    return `/bookings/${encodeURIComponent(bookingId)}`;
  }

  private toMessage(error: unknown): string {
    if (error instanceof BookingValidationError) {
      return error.message;
    }
    if (error instanceof Error && error.name === 'BookingNotFoundError') {
      return error.message;
    }
    this.logger.error(`経路・確定処理の予期せぬエラー: ${String(error)}`);
    return '処理に失敗しました。時間をおいて再度お試しください。';
  }
}
