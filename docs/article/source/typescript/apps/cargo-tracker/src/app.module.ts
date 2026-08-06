import { Module } from '@nestjs/common';
import { APP_GUARD } from '@nestjs/core';
import { EventEmitterModule } from '@nestjs/event-emitter';
import { ServeStaticModule } from '@nestjs/serve-static';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { HealthController } from './shared/infrastructure/web/health.controller.js';
import { AuthenticatedGuard } from './shared/presentation/auth/authenticated.guard.js';
import { SecurityModule } from './shared/security.module.js';
import { ClockModule } from './shared/infrastructure/clock/clock.module.js';
import { ShipperModule } from './contexts/shipper/shipper.module.js';
import { EstimationModule } from './contexts/estimation/estimation.module.js';
import { BookingModule } from './contexts/booking/booking.module.js';
import { RoutingModule } from './contexts/routing/routing.module.js';
import { HandlingModule } from './contexts/handling/handling.module.js';
import { TrackingModule } from './contexts/tracking/tracking.module.js';
import { BillingModule } from './contexts/billing/billing.module.js';

const rootDir = dirname(fileURLToPath(import.meta.url));

/**
 * ルートモジュール（合成ルート）。
 * 各境界付けられたコンテキストのモジュールをここで配線する。
 */
@Module({
  imports: [
    EventEmitterModule.forRoot(),
    ClockModule,
    ServeStaticModule.forRoot({
      // src/ から見た ../public。ビルド後（dist/）も同階層構成のため相対解決する。
      rootPath: join(rootDir, '..', 'public'),
      serveRoot: '/',
    }),
    SecurityModule,
    ShipperModule,
    EstimationModule,
    BookingModule,
    RoutingModule,
    HandlingModule,
    TrackingModule,
    BillingModule,
  ],
  controllers: [HealthController],
  providers: [
    // 認証を fail-closed 化する（デフォルト保護・@Public で明示除外。ADR-011）
    { provide: APP_GUARD, useClass: AuthenticatedGuard },
  ],
})
export class AppModule {}
