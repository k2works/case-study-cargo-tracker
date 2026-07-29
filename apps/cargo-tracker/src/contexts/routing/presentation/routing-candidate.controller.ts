import { Controller, Get, Inject, Query, Res, UseGuards } from '@nestjs/common';
import type { Response } from 'express';
import { renderFragment } from '../../../views/render.js';
import { RouteCandidateTable } from '../../../views/routing/RouteCandidateTable.js';
import { CargoType, isCargoType } from '../../../shared/domain/model/cargo-type.js';
import { Role } from '../../../shared/domain/model/role.js';
import { AuthenticatedGuard } from '../../../shared/presentation/auth/authenticated.guard.js';
import { RolesGuard } from '../../../shared/presentation/auth/roles.guard.js';
import { Roles } from '../../../shared/presentation/auth/roles.decorator.js';
import { RoutingQuery } from '../domain/model/route-candidate-finder.js';
import type { ExternalRoutingServicePort } from '../application/outboundservices/external-routing-service-port.js';
import type { VoyageRepository } from '../domain/repository/voyage-repository.js';
import { EXTERNAL_ROUTING_SERVICE, VOYAGE_REPOSITORY } from '../routing.tokens.js';

@Controller('routing/candidates')
@UseGuards(AuthenticatedGuard, RolesGuard)
@Roles(Role.ROUTE_DESIGNER)
export class RoutingCandidateController {
  constructor(
    @Inject(VOYAGE_REPOSITORY) private readonly voyages: VoyageRepository,
    @Inject(EXTERNAL_ROUTING_SERVICE) private readonly routingService: ExternalRoutingServicePort,
  ) {}

  @Get()
  async candidates(@Query() query: Record<string, string | undefined>, @Res() res: Response): Promise<void> {
    const rawCargoType = query.cargoType ?? '';
    const cargoType = isCargoType(rawCargoType) ? rawCargoType : CargoType.GENERAL;
    const routingQuery = RoutingQuery.of({
      origin: query.origin ?? '',
      destination: query.destination ?? '',
      arrivalDeadline: new Date(query.arrivalDeadline ?? ''),
      cargoType,
    });
    const voyages = await this.voyages.findAll();
    const candidates = await this.routingService.findCandidates(routingQuery, voyages);
    renderFragment(res, RouteCandidateTable({ candidates }));
  }
}
