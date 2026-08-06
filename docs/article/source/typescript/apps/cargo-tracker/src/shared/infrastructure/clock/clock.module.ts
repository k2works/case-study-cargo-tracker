import { Global, Module } from '@nestjs/common';
import { CLOCK, systemClock } from './clock.js';

/**
 * Clock を全 BC へ供給するグローバルモジュール（IT7 1.4）。
 * 既定は実時刻。テストでは CLOCK プロバイダを固定時刻へ差し替える。
 */
@Global()
@Module({
  providers: [{ provide: CLOCK, useValue: systemClock }],
  exports: [CLOCK],
})
export class ClockModule {}
