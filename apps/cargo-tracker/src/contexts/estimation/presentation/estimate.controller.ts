import {
  Body,
  Controller,
  Get,
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
import { renderPage, renderFragment } from '../../../views/render.js';
import { NewEstimate } from '../../../views/estimation/New.js';
import { IndexEstimate } from '../../../views/estimation/Index.js';
import { ShowEstimate } from '../../../views/estimation/Show.js';
import { HazardousFields, EmptyHazardousFields } from '../../../views/estimation/HazardousFields.js';
import { Role } from '../../../shared/domain/model/role.js';
import { CargoType, isCargoType } from '../../../shared/domain/model/cargo-type.js';
import { AuthenticatedGuard } from '../../../shared/presentation/auth/authenticated.guard.js';
import { RolesGuard } from '../../../shared/presentation/auth/roles.guard.js';
import { Roles } from '../../../shared/presentation/auth/roles.decorator.js';
import { CreateEstimateService } from '../application/commandservices/create-estimate.service.js';
import {
  EstimateQueryService,
  type EstimateDetail,
} from '../application/queryservices/estimate-query.service.js';

/**
 * 見積コントローラ（US01）。営業担当者のみ到達可能。
 */
@Controller('estimates')
@UseGuards(AuthenticatedGuard, RolesGuard)
@Roles(Role.SALES)
export class EstimateController {
  private readonly logger = new Logger(EstimateController.name);

  constructor(
    private readonly createService: CreateEstimateService,
    private readonly queryService: EstimateQueryService,
  ) {}

  @Get()
  async index(@Req() req: Request, @Res() res: Response): Promise<void> {
    const estimates = await this.queryService.list();
    renderPage(res, IndexEstimate({ user: req.session.user!, estimates }));
  }

  @Get('new')
  showNew(@Req() req: Request, @Res() res: Response): void {
    renderPage(res, NewEstimate({ user: req.session.user! }));
  }

  /** htmx: 貨物種別に応じた危険物フィールドのフラグメント */
  @Get('fields')
  fields(@Query('cargoType') cargoType: string, @Res() res: Response): void {
    if (cargoType === CargoType.HAZARDOUS) {
      renderFragment(res, HazardousFields());
      return;
    }
    renderFragment(res, EmptyHazardousFields());
  }

  @Post()
  async create(
    @Body() body: Record<string, string>,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const cargoType = isCargoType(body.cargoType) ? body.cargoType : CargoType.GENERAL;
    try {
      const result = await this.createService.create({
        origin: body.origin ?? '',
        destination: body.destination ?? '',
        arrivalDeadline: new Date(body.arrivalDeadline ?? ''),
        cargoType,
        weightKg: Number(body.weightKg),
      });
      this.logger.log(`見積作成: ${result.estimateId} deadlineMet=${result.deadlineMet}`);
      res.redirect(`/estimates/${result.estimateId}`);
    } catch (error) {
      const message = error instanceof Error ? error.message : '見積の作成に失敗しました';
      this.logger.warn(`見積作成失敗: ${message}`);
      res.status(200);
      renderPage(
        res,
        NewEstimate({
          user: req.session.user!,
          error: message,
          values: { cargoType, origin: body.origin, destination: body.destination },
        }),
      );
    }
  }

  @Get(':estimateId')
  async show(
    @Param('estimateId') estimateId: string,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const estimate = await this.queryService.findDetail(estimateId);
    if (estimate === null) {
      throw new NotFoundException('見積が見つかりません');
    }
    renderPage(
      res,
      ShowEstimate({
        user: req.session.user!,
        estimate,
        deadlineMet: this.deadlineMet(estimate),
      }),
    );
  }

  /** 詳細表示時に期限充足を再計算する（候補の最短所要日数で判定） */
  private deadlineMet(estimate: EstimateDetail): boolean {
    if (estimate.candidates.length === 0) {
      return false;
    }
    const now = new Date();
    const deadline = new Date(estimate.arrivalDeadline);
    return estimate.candidates.some((c) => {
      const eta = new Date(now.getTime() + c.transitDays * 24 * 60 * 60 * 1000);
      return eta.getTime() <= deadline.getTime();
    });
  }
}
