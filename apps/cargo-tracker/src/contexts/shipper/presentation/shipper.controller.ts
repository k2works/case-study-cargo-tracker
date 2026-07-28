import { Body, Controller, Get, Logger, Post, Query, Req, Res, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { renderPage, renderFragment } from '../../../views/render.js';
import { NewShipper } from '../../../views/shipper/New.js';
import {
  CorporateFields,
  EmptyCorporateFields,
} from '../../../views/shipper/CorporateFields.js';
import { Role } from '../../../shared/domain/model/role.js';
import { AuthenticatedGuard } from '../../../shared/presentation/auth/authenticated.guard.js';
import { RolesGuard } from '../../../shared/presentation/auth/roles.guard.js';
import { Roles } from '../../../shared/presentation/auth/roles.decorator.js';
import { ShipperType } from '../domain/model/value-objects.js';
import { ShipperValidationError } from '../domain/model/shipper-validation-error.js';
import { EmailAlreadyRegisteredError } from '../domain/repository/shipper-repository.js';
import { RegisterShipperService } from '../application/commandservices/register-shipper.service.js';

/**
 * 荷主登録コントローラ（US02/US03）。営業担当者のみ到達可能。
 */
@Controller('shippers')
@UseGuards(AuthenticatedGuard, RolesGuard)
@Roles(Role.SALES)
export class ShipperController {
  private readonly logger = new Logger(ShipperController.name);

  constructor(private readonly registerService: RegisterShipperService) {}

  @Get('new')
  showNew(@Req() req: Request, @Res() res: Response): void {
    renderPage(res, NewShipper({ user: req.session.user! }));
  }

  /** htmx: 荷主種別に応じた法人フィールドのフラグメントを返す */
  @Get('fields')
  fields(@Query('shipperType') shipperType: string, @Res() res: Response): void {
    if (shipperType === ShipperType.CORPORATE) {
      renderFragment(res, CorporateFields({}));
      return;
    }
    renderFragment(res, EmptyCorporateFields());
  }

  @Post()
  async create(
    @Body() body: Record<string, string>,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const shipperType =
      body.shipperType === ShipperType.CORPORATE
        ? ShipperType.CORPORATE
        : ShipperType.INDIVIDUAL;
    try {
      const result = await this.registerService.register({
        shipperType,
        name: body.name ?? '',
        email: body.email ?? '',
        phone: body.phone,
        address: body.address,
        contractNumber: body.contractNumber,
        discountRate: parseDiscountPercent(body.discountRate),
      });
      this.logger.log(`荷主登録: id=${result.id} code=${result.shipperCode} type=${shipperType}`);
      req.session.flash = { success: `荷主を登録しました（荷主 ID: ${result.shipperCode}）` };
      res.redirect('/');
    } catch (error) {
      const message = this.toErrorMessage(error);
      this.logger.warn(`荷主登録失敗: ${message}`);
      res.status(200);
      renderPage(
        res,
        NewShipper({
          user: req.session.user!,
          error: message,
          values: {
            shipperType,
            name: body.name,
            email: body.email,
            phone: body.phone,
            address: body.address,
          },
        }),
      );
    }
  }

  /** 登録失敗時の例外を利用者向けメッセージに変換する */
  private toErrorMessage(error: unknown): string {
    if (error instanceof EmailAlreadyRegisteredError) {
      return `${error.email} は既に荷主 ID: ${error.existingShipperCode} として登録されています。既存の荷主をご利用ください。`;
    }
    if (error instanceof ShipperValidationError) {
      return error.message;
    }
    // 想定外（インフラ障害等）の内部メッセージは画面へ出さない
    this.logger.error(`荷主登録の予期せぬエラー: ${String(error)}`);
    return '荷主の登録に失敗しました。時間をおいて再度お試しください。';
  }
}

/** パーセント表記（0〜30）を割引率（0〜0.3）に変換する。未入力は undefined */
export function parseDiscountPercent(value: string | undefined): number | undefined {
  if (value === undefined || value === '') {
    return undefined;
  }
  return Number(value) / 100;
}
