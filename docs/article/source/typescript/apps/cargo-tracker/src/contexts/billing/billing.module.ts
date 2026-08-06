import { Module } from '@nestjs/common';
import { DATABASE, type AppDatabase } from '../../shared/infrastructure/database/database.js';
import { CLOCK, type Clock } from '../../shared/infrastructure/clock/clock.js';
import { NotificationRecorder } from '../../shared/infrastructure/notification/notification-recorder.js';
import { KyselyInvoiceRepository } from './infrastructure/repositories/kysely-invoice-repository.js';
import { KyselyBillingSnapshot } from './infrastructure/repositories/kysely-billing-snapshot.js';
import { RecordingBillingNotificationService } from './infrastructure/services/recording-billing-notification.service.js';
import { StubPaymentGateway } from './infrastructure/services/stub-payment-gateway.js';
import { BillingEventEmitterPublisher } from './infrastructure/services/event-emitter-publisher.js';
import type { InvoiceRepository } from './domain/repository/invoice-repository.js';
import type { BillingSnapshotAcl } from './application/outboundservices/acl/billing-snapshot-acl.js';
import type { BillingNotificationPort } from './application/outboundservices/acl/billing-notification-port.js';
import type { PaymentGatewayPort } from './application/outboundservices/acl/payment-gateway-port.js';
import { GenerateInvoiceService } from './application/commandservices/generate-invoice.service.js';
import { ConfirmPaymentService, type EventPublisher } from './application/commandservices/confirm-payment.service.js';
import { MarkOverdueService } from './application/commandservices/mark-overdue.service.js';
import { BillingQueryService } from './application/queryservices/billing-query.service.js';
import { BillingController } from './presentation/billing.controller.js';
import {
  INVOICE_REPOSITORY,
  BILLING_SNAPSHOT_ACL,
  BILLING_NOTIFICATION_PORT,
  PAYMENT_GATEWAY_PORT,
  GENERATE_INVOICE_SERVICE,
  CONFIRM_PAYMENT_SERVICE,
  MARK_OVERDUE_SERVICE,
  BILLING_QUERY_SERVICE,
  BILLING_EVENT_PUBLISHER,
} from './billing.tokens.js';

export {
  INVOICE_REPOSITORY,
  BILLING_SNAPSHOT_ACL,
  BILLING_NOTIFICATION_PORT,
  PAYMENT_GATEWAY_PORT,
  GENERATE_INVOICE_SERVICE,
  CONFIRM_PAYMENT_SERVICE,
  MARK_OVERDUE_SERVICE,
  BILLING_QUERY_SERVICE,
};

/**
 * Billing Context の配線（US21 料金算出・US22 法人割引・US23 精算処理）。
 * 輸送実績・割引率・荷主メールは BillingSnapshotAcl（参照専用スナップショット）で取得し、
 * 他 BC のドメイン型に依存しない（ADR-008）。SETTLED 反映はイベント（PAYMENT_CONFIRMED_EVENT）+
 * Booking 側の冪等リスナーで連携する（ADR-005/009）。
 */
@Module({
  controllers: [BillingController],
  providers: [
    BillingEventEmitterPublisher,
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
      provide: NotificationRecorder,
      useFactory: (db: AppDatabase): NotificationRecorder => new NotificationRecorder(db),
      inject: [DATABASE],
    },
    {
      provide: BILLING_NOTIFICATION_PORT,
      useFactory: (recorder: NotificationRecorder): BillingNotificationPort =>
        new RecordingBillingNotificationService(recorder),
      inject: [NotificationRecorder],
    },
    {
      provide: PAYMENT_GATEWAY_PORT,
      useFactory: (clock: Clock): PaymentGatewayPort => new StubPaymentGateway(clock),
      inject: [CLOCK],
    },
    {
      provide: BILLING_EVENT_PUBLISHER,
      useExisting: BillingEventEmitterPublisher,
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
        notifier: BillingNotificationPort,
        clock: Clock,
      ): GenerateInvoiceService => new GenerateInvoiceService(invoices, snapshots, notifier, clock),
      inject: [INVOICE_REPOSITORY, BILLING_SNAPSHOT_ACL, BILLING_NOTIFICATION_PORT, CLOCK],
    },
    {
      provide: CONFIRM_PAYMENT_SERVICE,
      useFactory: (
        invoices: InvoiceRepository,
        gateway: PaymentGatewayPort,
        publisher: EventPublisher,
      ): ConfirmPaymentService => new ConfirmPaymentService(invoices, gateway, publisher),
      inject: [INVOICE_REPOSITORY, PAYMENT_GATEWAY_PORT, BILLING_EVENT_PUBLISHER],
    },
    {
      provide: MARK_OVERDUE_SERVICE,
      useFactory: (
        invoices: InvoiceRepository,
        notifier: BillingNotificationPort,
        clock: Clock,
      ): MarkOverdueService => new MarkOverdueService(invoices, notifier, clock),
      inject: [INVOICE_REPOSITORY, BILLING_NOTIFICATION_PORT, CLOCK],
    },
  ],
  exports: [BILLING_QUERY_SERVICE],
})
export class BillingModule {}
