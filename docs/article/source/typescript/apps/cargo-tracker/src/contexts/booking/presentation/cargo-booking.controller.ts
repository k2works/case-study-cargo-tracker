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
import { DATABASE, type AppDatabase } from '../../../shared/infrastructure/database/database.js';
import { listLocations } from '../../../shared/infrastructure/database/location-query.js';
import { renderPage, renderFragment } from '../../../views/render.js';
import { NewBooking } from '../../../views/booking/New.js';
import { IndexBooking } from '../../../views/booking/Index.js';
import { ShowBooking } from '../../../views/booking/Show.js';
import { CargoTypeFields } from '../../../views/booking/CargoTypeFields.js';
import { Role } from '../../../shared/domain/model/role.js';
import { CargoType, isCargoType } from '../../../shared/domain/model/cargo-type.js';
import { RolesGuard } from '../../../shared/presentation/auth/roles.guard.js';
import { Roles } from '../../../shared/presentation/auth/roles.decorator.js';
import { BookingStatus } from '../domain/model/booking-status.js';
import { BookingValidationError } from '../domain/model/booking-validation-error.js';
import { ShipperNotFoundError } from '../application/outboundservices/acl/shipper-existence-checker.js';
import { BookCargoService, type BookCargoCommand } from '../application/commandservices/book-cargo.service.js';
import { AssignToRoutingService } from '../application/commandservices/assign-to-routing.service.js';
import { BookingQueryService } from '../application/queryservices/booking-query.service.js';

/**
 * 貨物予約コントローラ（US04/US05/US06）。
 * 一覧・詳細は複数ロールが到達（営業/荷主/経路設計者）。登録・引き渡しは営業担当者のみ。
 */
@Controller('bookings')
@UseGuards(RolesGuard)
export class CargoBookingController {
  private readonly logger = new Logger(CargoBookingController.name);

  constructor(
    private readonly bookService: BookCargoService,
    private readonly assignService: AssignToRoutingService,
    private readonly queryService: BookingQueryService,
    @Inject(DATABASE) private readonly db: AppDatabase,
  ) {}

  @Get()
  @Roles(Role.SALES, Role.SHIPPER, Role.ROUTE_DESIGNER)
  async index(@Query('status') status: string | undefined, @Req() req: Request, @Res() res: Response): Promise<void> {
    const bookings = await this.queryService.list(status);
    renderPage(
      res,
      IndexBooking({
        user: req.session.user!,
        bookings,
        routingWaiting: status === BookingStatus.ROUTING_IN_PROGRESS,
      }),
    );
  }

  @Get('new')
  @Roles(Role.SALES)
  async showNew(@Req() req: Request, @Res() res: Response): Promise<void> {
    const locations = await listLocations(this.db);
    renderPage(res, NewBooking({ user: req.session.user!, locations }));
  }

  /** htmx: 貨物種別に応じた条件フィールド */
  @Get('fields')
  @Roles(Role.SALES)
  fields(@Query('cargoType') cargoType: string, @Res() res: Response): void {
    renderFragment(res, CargoTypeFields({ cargoType }));
  }

  @Post()
  @Roles(Role.SALES)
  async create(@Body() body: Record<string, string>, @Req() req: Request, @Res() res: Response): Promise<void> {
    const cargoType = isCargoType(body.cargoType) ? body.cargoType : CargoType.GENERAL;
    try {
      const result = await this.bookService.book(this.toCommand(body, cargoType));
      this.logger.log(`貨物予約登録: ${result.bookingId}`);
      req.session.flash = { success: `貨物予約を登録しました（予約番号: ${result.bookingId}）` };
      res.redirect(`/bookings/${encodeURIComponent(result.bookingId)}`);
    } catch (error) {
      const message = this.toErrorMessage(error);
      this.logger.warn(`貨物予約登録失敗: ${message}`);
      res.status(200);
      const locations = await listLocations(this.db);
      renderPage(res, NewBooking({ user: req.session.user!, error: message, locations, values: body }));
    }
  }

  @Get(':bookingId')
  @Roles(Role.SALES, Role.SHIPPER, Role.ROUTE_DESIGNER)
  async show(@Param('bookingId') bookingId: string, @Req() req: Request, @Res() res: Response): Promise<void> {
    const booking = await this.queryService.findDetail(bookingId);
    if (booking === null) {
      throw new NotFoundException('予約が見つかりません');
    }
    const success = req.session.flash?.success;
    req.session.flash = {};
    renderPage(res, ShowBooking({ user: req.session.user!, booking, success }));
  }

  @Post(':bookingId/assign-to-routing')
  @Roles(Role.SALES)
  async assignToRouting(@Param('bookingId') bookingId: string, @Req() req: Request, @Res() res: Response): Promise<void> {
    try {
      await this.assignService.assign(bookingId);
      req.session.flash = { success: '経路設計者へ引き渡しました（経路設計中）' };
    } catch (error) {
      req.session.flash = { error: this.toErrorMessage(error) };
    }
    // パスパラメータ由来の値はエンコードして open redirect を防ぐ
    res.redirect(`/bookings/${encodeURIComponent(bookingId)}`);
  }

  private toCommand(body: Record<string, string>, cargoType: CargoType): BookCargoCommand {
    return {
      shipperCode: body.shipperCode ?? '',
      cargoType,
      weightKg: Number(body.weightKg),
      origin: body.origin ?? '',
      destination: body.destination ?? '',
      arrivalDeadline: new Date(body.arrivalDeadline ?? ''),
      consignee: {
        name: body.consigneeName ?? '',
        address: body.consigneeAddress ?? '',
        contactEmail: body.consigneeEmail ?? '',
      },
      quantity: body.quantity ? Number(body.quantity) : undefined,
      description: body.description || undefined,
      hazardous:
        cargoType === CargoType.HAZARDOUS
          ? {
              hazardousClass: body.hazardousClass ?? '',
              unNumber: body.unNumber ?? '',
              properShippingName: body.properShippingName ?? '',
            }
          : undefined,
      temperature:
        cargoType === CargoType.REFRIGERATED
          ? {
              minTemperature: toNumberOrNaN(body.minTemperature),
              maxTemperature: toNumberOrNaN(body.maxTemperature),
              unit: body.temperatureUnit ?? 'CELSIUS',
            }
          : undefined,
    };
  }

  private toErrorMessage(error: unknown): string {
    if (error instanceof ShipperNotFoundError) {
      return `${error.shipperCode} に該当する荷主が見つかりません。荷主 ID をご確認ください。`;
    }
    if (error instanceof BookingValidationError) {
      return error.message;
    }
    if (error instanceof Error && error.name === 'BookingNotFoundError') {
      return error.message;
    }
    this.logger.error(`予約処理の予期せぬエラー: ${String(error)}`);
    return '予約処理に失敗しました。時間をおいて再度お試しください。';
  }
}

/** 空文字・未入力を NaN として扱う数値変換（Number('') が 0 になる罠を回避） */
function toNumberOrNaN(value: string | undefined): number {
  if (value === undefined || value.trim() === '') {
    return Number.NaN;
  }
  return Number(value);
}
