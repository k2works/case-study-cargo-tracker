import { Controller, Get, Query, Res, UseGuards } from '@nestjs/common';
import type { Response } from 'express';
import { renderFragment } from '../../../views/render.js';
import { RouteCandidateTable } from '../../../views/routing/RouteCandidateTable.js';
import { Role } from '../../../shared/domain/model/role.js';
import { RolesGuard } from '../../../shared/presentation/auth/roles.guard.js';
import { Roles } from '../../../shared/presentation/auth/roles.decorator.js';
import { FindRouteCandidatesService } from '../application/queryservices/find-route-candidates.service.js';

/**
 * 経路候補フラグメント（US08）。htmx 部分更新で候補テーブルを返す。
 * 候補算出の組み立ては FindRouteCandidatesService に委譲する（IT4 Try T2）。
 */
@Controller('routing/candidates')
@UseGuards(RolesGuard)
@Roles(Role.ROUTE_DESIGNER)
export class RoutingCandidateController {
  constructor(private readonly finder: FindRouteCandidatesService) {}

  @Get()
  async candidates(@Query() query: Record<string, string | undefined>, @Res() res: Response): Promise<void> {
    const candidates = await this.finder.find({
      origin: query.origin ?? '',
      destination: query.destination ?? '',
      arrivalDeadline: query.arrivalDeadline ?? '',
      cargoType: query.cargoType ?? '',
    });
    renderFragment(res, RouteCandidateTable({ candidates }));
  }
}
