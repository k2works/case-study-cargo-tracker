import { Body, Controller, Get, Param, Post, Query, Req, Res, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { renderFragment, renderPage } from '../../../views/render.js';
import { IndexVoyage, VoyageTable } from '../../../views/routing/Index.js';
import { VoyageForm } from '../../../views/routing/VoyageForm.js';
import { Role } from '../../../shared/domain/model/role.js';
import { CargoType, isCargoType } from '../../../shared/domain/model/cargo-type.js';
import { AuthenticatedGuard } from '../../../shared/presentation/auth/authenticated.guard.js';
import { RolesGuard } from '../../../shared/presentation/auth/roles.guard.js';
import { Roles } from '../../../shared/presentation/auth/roles.decorator.js';
import { RegisterVoyageService } from '../application/commandservices/register-voyage.service.js';
import { UpdateScheduleService } from '../application/commandservices/update-schedule.service.js';
import { VoyageQueryService } from '../application/queryservices/voyage-query.service.js';
import { RoutingValidationError } from '../domain/model/routing-validation-error.js';

@Controller('voyages')
@UseGuards(AuthenticatedGuard, RolesGuard)
@Roles(Role.ROUTE_DESIGNER)
export class VoyageController {
  constructor(
    private readonly registerService: RegisterVoyageService,
    private readonly updateService: UpdateScheduleService,
    private readonly queryService: VoyageQueryService,
  ) {}

  @Get()
  async index(
    @Query() query: Record<string, string | undefined>,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const criteria = {
      origin: query.origin,
      destination: query.destination,
      cargoType: query.cargoType,
    };
    const voyages = await this.queryService.list(criteria);
    if (req.headers['hx-request'] === 'true') {
      renderFragment(res, VoyageTable({ voyages }));
      return;
    }
    const searching = Object.values(criteria).some((value) => value !== undefined && value.trim() !== '');
    const success = searching ? undefined : req.session.flash?.success;
    req.session.flash = {};
    renderPage(res, IndexVoyage({ user: req.session.user!, voyages, success }));
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
        carrierMovements: [toMovement(body)],
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
        carrierMovements: [toMovement(body)],
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
}

function toCargoTypes(value: string | string[] | undefined): CargoType[] {
  const values = Array.isArray(value) ? value : value ? [value] : [];
  return values.filter(isCargoType);
}

function toMovement(body: VoyageFormBody) {
  return {
    departureLocation: body.departureLocation ?? '',
    arrivalLocation: body.arrivalLocation ?? '',
    departureTime: parseDatetimeLocal(body.departureTime),
    arrivalTime: parseDatetimeLocal(body.arrivalTime),
  };
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

function parseDatetimeLocal(value: string | undefined): Date {
  if (value === undefined || value.trim() === '') {
    return new Date('');
  }
  return new Date(`${value}:00Z`);
}
