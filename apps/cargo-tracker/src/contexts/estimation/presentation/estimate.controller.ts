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
import { NewEstimate } from '../../../views/estimation/New.js';
import { IndexEstimate } from '../../../views/estimation/Index.js';
import { ShowEstimate } from '../../../views/estimation/Show.js';
import { HazardousFields, EmptyHazardousFields } from '../../../views/estimation/HazardousFields.js';
import { Role } from '../../../shared/domain/model/role.js';
import { CargoType, isCargoType } from '../../../shared/domain/model/cargo-type.js';
import { AuthenticatedGuard } from '../../../shared/presentation/auth/authenticated.guard.js';
import { RolesGuard } from '../../../shared/presentation/auth/roles.guard.js';
import { Roles } from '../../../shared/presentation/auth/roles.decorator.js';
import { SharedValidationError } from '../../../shared/domain/model/shared-validation-error.js';
import { EstimateValidationError } from '../domain/model/estimate-validation-error.js';
import { Estimate } from '../domain/model/estimate.js';
import { RouteCandidate } from '../domain/model/route-candidate.js';
import { EstimateStatus } from '../domain/model/estimate-status.js';
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
    @Inject(DATABASE) private readonly db: AppDatabase,
  ) {}

  @Get()
  async index(@Req() req: Request, @Res() res: Response): Promise<void> {
    const estimates = await this.queryService.list();
    renderPage(res, IndexEstimate({ user: req.session.user!, estimates }));
  }

  @Get('new')
  async showNew(@Req() req: Request, @Res() res: Response): Promise<void> {
    const locations = await listLocations(this.db);
    renderPage(res, NewEstimate({ user: req.session.user!, locations }));
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
      res.redirect(`/estimates/${encodeURIComponent(result.estimateId)}`);
    } catch (error) {
      const message = this.toErrorMessage(error);
      this.logger.warn(`見積作成失敗: ${message}`);
      res.status(200);
      const locations = await listLocations(this.db);
      renderPage(
        res,
        NewEstimate({
          user: req.session.user!,
          error: message,
          locations,
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

  /** 検証エラーは利用者へ提示し、内部エラーは汎用メッセージ + error ログとする */
  private toErrorMessage(error: unknown): string {
    if (error instanceof EstimateValidationError || error instanceof SharedValidationError) {
      return error.message;
    }
    this.logger.error(`見積作成の予期せぬエラー: ${String(error)}`);
    return '見積の作成に失敗しました。時間をおいて再度お試しください。';
  }

  /** 詳細表示時に期限充足を再計算する（Estimate 集約の判定ロジックへ委譲） */
  private deadlineMet(detail: EstimateDetail): boolean {
    const cargoType = isCargoType(detail.cargoType) ? detail.cargoType : CargoType.GENERAL;
    const estimate = Estimate.reconstruct({
      id: 0,
      estimateId: detail.estimateId,
      origin: detail.origin,
      destination: detail.destination,
      arrivalDeadline: new Date(detail.arrivalDeadline),
      cargoType,
      weightKg: Number(detail.weightKg),
      status: EstimateStatus.CREATED,
      candidates: detail.candidates.map((c) =>
        RouteCandidate.of({
          voyageNumber: c.voyageNumber,
          transitPort: c.transitPort,
          transitDays: c.transitDays,
          estimatedCost: Number(c.estimatedCost),
        }),
      ),
    });
    return estimate.isDeadlineMet(new Date());
  }
}
