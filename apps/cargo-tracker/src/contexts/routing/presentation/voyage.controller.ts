import { Body, Controller, Get, Inject, Param, Post, Query, Req, Res, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { renderFragment, renderPage } from '../../../views/render.js';
import { IndexVoyage, VoyageTable } from '../../../views/routing/Index.js';
import { VoyageForm } from '../../../views/routing/VoyageForm.js';
import { VoyageUpdateConfirm } from '../../../views/routing/VoyageUpdateConfirm.js';
import { Role } from '../../../shared/domain/model/role.js';
import { CargoType, isCargoType } from '../../../shared/domain/model/cargo-type.js';
import { AuthenticatedGuard } from '../../../shared/presentation/auth/authenticated.guard.js';
import { RolesGuard } from '../../../shared/presentation/auth/roles.guard.js';
import { Roles } from '../../../shared/presentation/auth/roles.decorator.js';
import { RegisterVoyageService } from '../application/commandservices/register-voyage.service.js';
import { UpdateScheduleService } from '../application/commandservices/update-schedule.service.js';
import {
  VoyageQueryService,
  type VoyageSearchCriteria,
} from '../application/queryservices/voyage-query.service.js';
import { RoutingValidationError } from '../domain/model/routing-validation-error.js';
import type { RoutingBookingConditionReader } from '../application/outboundservices/acl/routing-booking-condition-reader.js';
import { ROUTING_BOOKING_CONDITION_READER } from '../routing.tokens.js';

@Controller('voyages')
@UseGuards(AuthenticatedGuard, RolesGuard)
@Roles(Role.ROUTE_DESIGNER)
export class VoyageController {
  constructor(
    private readonly registerService: RegisterVoyageService,
    private readonly updateService: UpdateScheduleService,
    private readonly queryService: VoyageQueryService,
    @Inject(ROUTING_BOOKING_CONDITION_READER)
    private readonly bookingConditionReader: RoutingBookingConditionReader,
  ) {}

  @Get()
  async index(
    @Query() query: Record<string, string | undefined>,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const bookingCondition = query.bookingId
      ? await this.bookingConditionReader.findRoutingInProgress(query.bookingId)
      : null;
    const criteria: VoyageSearchCriteria = {
      origin: bookingCondition?.origin ?? query.origin,
      destination: bookingCondition?.destination ?? query.destination,
      cargoType: bookingCondition?.cargoType ?? query.cargoType,
      departureFrom: query.departureFrom,
      departureTo: query.departureTo,
      arrivalDeadline: bookingCondition ? toDateOnly(bookingCondition.arrivalDeadline) : query.arrivalDeadline,
    };
    const voyages = await this.queryService.list(criteria);
    if (req.headers['hx-request'] === 'true') {
      renderFragment(res, VoyageTable({ voyages, searched: isSearching(criteria) }));
      return;
    }
    const searching = isSearching(criteria);
    const success = searching ? undefined : req.session.flash?.success;
    req.session.flash = {};
    renderPage(
      res,
      IndexVoyage({
        user: req.session.user!,
        voyages,
        success,
        criteria,
        searching,
        bookingCondition: bookingCondition
          ? {
              bookingId: bookingCondition.bookingId,
              origin: bookingCondition.origin,
              destination: bookingCondition.destination,
              cargoType: bookingCondition.cargoType,
              arrivalDeadline: new Date(bookingCondition.arrivalDeadline),
            }
          : undefined,
      }),
    );
  }

  @Get('new')
  new(@Req() req: Request, @Res() res: Response): void {
    renderPage(res, VoyageForm({ user: req.session.user!, mode: 'new' }));
  }

  @Post()
  async create(@Body() body: VoyageFormBody, @Req() req: Request, @Res() res: Response): Promise<void> {
    try {
      await this.registerService.register({
        voyageNumber: body.voyageNumber ?? '',
        shipName: body.shipName ?? '',
        carrierName: body.carrierName ?? '',
        supportedCargoTypes: toCargoTypes(body.supportedCargoTypes),
        carrierMovements: toMovements(body),
      });
      req.session.flash = { success: `航海スケジュールを登録しました（航海番号: ${body.voyageNumber}）` };
      res.redirect('/voyages');
    } catch (error) {
      res.status(200);
      renderPage(
        res,
        VoyageForm({
          user: req.session.user!,
          mode: 'new',
          values: body,
          error: toErrorMessage(error),
        }),
      );
    }
  }

  @Get(':voyageNumber/edit')
  async edit(@Param('voyageNumber') voyageNumber: string, @Req() req: Request, @Res() res: Response): Promise<void> {
    const voyage = await this.queryService.find(voyageNumber);
    renderPage(
      res,
      VoyageForm({
        user: req.session.user!,
        mode: 'edit',
        values: voyage
          ? {
              voyageNumber: voyage.voyageNumber,
              shipName: voyage.shipName,
              carrierName: voyage.carrierName,
              departureLocation: voyage.departureLocation,
              arrivalLocation: voyage.arrivalLocation,
              departureTime: toDatetimeLocal(voyage.departureTime),
              arrivalTime: toDatetimeLocal(voyage.arrivalTime),
            }
          : { voyageNumber },
      }),
    );
  }

  @Post(':voyageNumber/confirm')
  async confirmUpdate(
    @Param('voyageNumber') voyageNumber: string,
    @Body() body: VoyageFormBody,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const voyage = await this.queryService.find(voyageNumber);
    if (voyage === null) {
      res.status(200);
      renderPage(
        res,
        VoyageForm({
          user: req.session.user!,
          mode: 'edit',
          values: { ...body, voyageNumber },
          error: toErrorMessage(new Error(`航海が見つかりません: ${voyageNumber}`)),
        }),
      );
      return;
    }
    const validationError = validateScheduleBody(body);
    if (validationError) {
      res.status(200);
      renderPage(
        res,
        VoyageForm({
          user: req.session.user!,
          mode: 'edit',
          values: { ...body, voyageNumber },
          error: validationError.message,
        }),
      );
      return;
    }

    res.status(200);
    renderPage(
      res,
      VoyageUpdateConfirm({
        user: req.session.user!,
        values: {
          voyageNumber,
          current: {
            departureLocation: voyage.departureLocation,
            arrivalLocation: voyage.arrivalLocation,
            departureTime: toDatetimeLocal(voyage.departureTime),
            arrivalTime: toDatetimeLocal(voyage.arrivalTime),
            transitLocation: voyage.transitPorts.join('、'),
            transitArrivalTime: '',
            transitDepartureTime: '',
          },
          updated: {
            departureLocation: body.departureLocation ?? '',
            arrivalLocation: body.arrivalLocation ?? '',
            departureTime: body.departureTime ?? '',
            arrivalTime: body.arrivalTime ?? '',
            transitLocation: body.transitLocation ?? '',
            transitArrivalTime: body.transitArrivalTime ?? '',
            transitDepartureTime: body.transitDepartureTime ?? '',
          },
        },
      }),
    );
  }

  @Post(':voyageNumber')
  async update(
    @Param('voyageNumber') voyageNumber: string,
    @Body() body: VoyageFormBody,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    try {
      await this.updateService.update({
        voyageNumber,
        carrierMovements: toMovements(body),
      });
      req.session.flash = { success: `航海スケジュールを更新しました（航海番号: ${voyageNumber}）` };
      res.redirect('/voyages');
    } catch (error) {
      res.status(200);
      renderPage(
        res,
        VoyageForm({
          user: req.session.user!,
          mode: 'edit',
          values: { ...body, voyageNumber },
          error: toErrorMessage(error),
        }),
      );
    }
  }

  @Post(':voyageNumber/cancel')
  cancelUpdate(@Param('voyageNumber') voyageNumber: string, @Req() req: Request, @Res() res: Response): void {
    req.session.flash = { success: `航海スケジュール更新をキャンセルしました（航海番号: ${voyageNumber}）` };
    res.redirect('/voyages');
  }
}

interface VoyageFormBody extends Record<string, string | string[] | undefined> {
  voyageNumber?: string;
  shipName?: string;
  carrierName?: string;
  supportedCargoTypes?: string | string[];
  departureLocation?: string;
  arrivalLocation?: string;
  departureTime?: string;
  arrivalTime?: string;
  transitLocation?: string;
  transitArrivalTime?: string;
  transitDepartureTime?: string;
}

function toCargoTypes(value: string | string[] | undefined): CargoType[] {
  let values: string[] = [];
  if (Array.isArray(value)) {
    values = value;
  } else if (value) {
    values = [value];
  }
  return values.filter(isCargoType);
}

function toMovements(body: VoyageFormBody) {
  const validationError = validateScheduleBody(body);
  if (validationError) {
    throw validationError;
  }
  if (!hasTransit(body)) {
    return [
      {
        departureLocation: body.departureLocation ?? '',
        arrivalLocation: body.arrivalLocation ?? '',
        departureTime: parseDatetimeLocal(body.departureTime),
        arrivalTime: parseDatetimeLocal(body.arrivalTime),
      },
    ];
  }
  return [
    {
      departureLocation: body.departureLocation ?? '',
      arrivalLocation: body.transitLocation ?? '',
      departureTime: parseDatetimeLocal(body.departureTime),
      arrivalTime: parseDatetimeLocal(body.transitArrivalTime),
    },
    {
      departureLocation: body.transitLocation ?? '',
      arrivalLocation: body.arrivalLocation ?? '',
      departureTime: parseDatetimeLocal(body.transitDepartureTime),
      arrivalTime: parseDatetimeLocal(body.arrivalTime),
    },
  ];
}

function validateScheduleBody(body: VoyageFormBody): Error | null {
  const requiredFields = [
    ['出発港', body.departureLocation],
    ['到着港', body.arrivalLocation],
    ['出発日時', body.departureTime],
    ['到着日時', body.arrivalTime],
  ] as const;
  const missing = requiredFields.filter(([, value]) => !hasText(value)).map(([label]) => label);
  if (missing.length > 0) {
    return new RoutingValidationError(`必須項目を入力してください: ${missing.join('、')}`);
  }
  const transitValues = [body.transitLocation, body.transitArrivalTime, body.transitDepartureTime];
  const hasAnyTransitValue = transitValues.some(hasText);
  const hasAllTransitValues = transitValues.every(hasText);
  if (hasAnyTransitValue && !hasAllTransitValues) {
    return new RoutingValidationError('寄港地、寄港到着日時、寄港出発日時をすべて入力してください');
  }
  // 更新確認画面へ進む前に日付の時系列を検証する（Try T6。invalid diff の確認 UX を避ける）
  return validateDateOrder(body);
}

/** 出発 → （寄港到着 → 寄港出発）→ 到着の時系列を検証する */
function validateDateOrder(body: VoyageFormBody): Error | null {
  const departure = parseDatetimeLocal(body.departureTime);
  const arrival = parseDatetimeLocal(body.arrivalTime);
  if (hasTransit(body)) {
    const transitArrival = parseDatetimeLocal(body.transitArrivalTime);
    const transitDeparture = parseDatetimeLocal(body.transitDepartureTime);
    if (departure > transitArrival) {
      return new RoutingValidationError('出発日時は寄港到着日時以前である必要があります');
    }
    if (transitArrival > transitDeparture) {
      return new RoutingValidationError('寄港到着日時は寄港出発日時以前である必要があります');
    }
    if (transitDeparture > arrival) {
      return new RoutingValidationError('寄港出発日時は到着日時以前である必要があります');
    }
    return null;
  }
  if (departure > arrival) {
    return new RoutingValidationError('出発日時は到着日時以前である必要があります');
  }
  return null;
}

function hasTransit(body: VoyageFormBody): boolean {
  return hasText(body.transitLocation) && hasText(body.transitArrivalTime) && hasText(body.transitDepartureTime);
}

function hasText(value: string | undefined): boolean {
  return value !== undefined && value.trim() !== '';
}

function isSearching(criteria: VoyageSearchCriteria): boolean {
  return Object.values(criteria).some((value) => value !== undefined && value.trim() !== '');
}

function toErrorMessage(error: unknown): string {
  if (error instanceof RoutingValidationError) {
    return error.message;
  }
  return error instanceof Error ? error.message : '航海スケジュールの処理に失敗しました';
}

function toDatetimeLocal(value: Date): string {
  return value.toISOString().slice(0, 16);
}

function toDateOnly(value: Date): string {
  return value.toISOString().slice(0, 10);
}

function parseDatetimeLocal(value: string | undefined): Date {
  if (value === undefined || value.trim() === '') {
    return new Date('');
  }
  return new Date(`${value}:00Z`);
}
