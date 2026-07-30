import { Module } from '@nestjs/common';
import { DATABASE, type AppDatabase } from '../../shared/infrastructure/database/database.js';
import { KyselyInvoiceRepository } from './infrastructure/repositories/kysely-invoice-repository.js';
import type { InvoiceRepository } from './domain/repository/invoice-repository.js';
import { INVOICE_REPOSITORY } from './billing.tokens.js';

export { INVOICE_REPOSITORY };

/**
 * Billing Context の配線（US21 請求書発行・US22 精算の基盤）。
 * 本 IT では骨格として InvoiceRepository ポートのみを束縛する。
 * 料金算出サービス・コントローラ・イベント購読はグループ 3/4 で追加する。
 */
@Module({
  controllers: [],
  providers: [
    {
      provide: INVOICE_REPOSITORY,
      useFactory: (db: AppDatabase): InvoiceRepository => new KyselyInvoiceRepository(db),
      inject: [DATABASE],
    },
  ],
})
export class BillingModule {}
