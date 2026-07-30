import { Module } from '@nestjs/common';
import { DATABASE, type AppDatabase } from '../../shared/infrastructure/database/database.js';
import { CLOCK, type Clock } from '../../shared/infrastructure/clock/clock.js';
import { KyselyInvoiceRepository } from './infrastructure/repositories/kysely-invoice-repository.js';
import { KyselyBillingSnapshot } from './infrastructure/repositories/kysely-billing-snapshot.js';
import type { InvoiceRepository } from './domain/repository/invoice-repository.js';
import type { BillingSnapshotAcl } from './application/outboundservices/acl/billing-snapshot-acl.js';
import { GenerateInvoiceService } from './application/commandservices/generate-invoice.service.js';
import { BillingQueryService } from './application/queryservices/billing-query.service.js';
import { BillingController } from './presentation/billing.controller.js';
import {
  INVOICE_REPOSITORY,
  BILLING_SNAPSHOT_ACL,
  GENERATE_INVOICE_SERVICE,
  BILLING_QUERY_SERVICE,
} from './billing.tokens.js';

export { INVOICE_REPOSITORY, BILLING_SNAPSHOT_ACL, GENERATE_INVOICE_SERVICE, BILLING_QUERY_SERVICE };

/**
 * Billing Context の配線（US21 料金算出・US22 法人割引・グループ 3）。
 * 輸送実績・割引率は BillingSnapshotAcl（参照専用スナップショット）で取得し、他 BC のドメイン型に
 * 依存しない（ADR-008）。入金確認・請求書詳細・期限超過はグループ 4 で追加する。
 */
@Module({
  controllers: [BillingController],
  providers: [
    {
      provide: INVOICE_REPOSITORY,
      useFactory: (db: AppDatabase): InvoiceRepository => new KyselyInvoiceRepository(db),
      inject: [DATABASE],
    },
    {
      provide: BILLING_SNAPSHOT_ACL,
      useFactory: (db: AppDatabase): BillingSnapshotAcl => new KyselyBillingSnapshot(db),
      inject: [DATABASE],
    },
    {
      provide: BILLING_QUERY_SERVICE,
      useFactory: (db: AppDatabase): BillingQueryService => new BillingQueryService(db),
      inject: [DATABASE],
    },
    {
      provide: GENERATE_INVOICE_SERVICE,
      useFactory: (
        invoices: InvoiceRepository,
        snapshots: BillingSnapshotAcl,
        clock: Clock,
      ): GenerateInvoiceService => new GenerateInvoiceService(invoices, snapshots, clock),
      inject: [INVOICE_REPOSITORY, BILLING_SNAPSHOT_ACL, CLOCK],
    },
  ],
  exports: [BILLING_QUERY_SERVICE],
})
export class BillingModule {}
