import { Logger } from '@nestjs/common';
import { type Clock, systemClock } from '../../../../shared/infrastructure/clock/clock.js';
import { NotificationType } from '../../../../shared/contracts/notification-type.js';
import type { InvoiceRepository } from '../../domain/repository/invoice-repository.js';
import type { BillingNotificationPort } from '../outboundservices/acl/billing-notification-port.js';

/** 未払い通知の宛先（経理担当者。ADR-012 の宛先設定値化に従い環境変数で差し替え可能） */
const ACCOUNTING_RECIPIENT = process.env.BILLING_ACCOUNTING_EMAIL ?? 'accounting@cargo-tracker.example';

/**
 * 期限超過判定ユースケース（US23-5）。
 * バッチ基盤が無いため、請求書一覧・詳細の照会時に呼び出して期限超過（発行 + 30 日）を判定し、
 * PENDING → OVERDUE の初回遷移時のみ経理担当者へ未払い通知を記録する（markOverdue の戻り値で制御）。
 * 判定は何度実行しても同値（冪等）。
 */
export class MarkOverdueService {
  private readonly logger = new Logger(MarkOverdueService.name);

  constructor(
    private readonly invoices: InvoiceRepository,
    private readonly notifier: BillingNotificationPort,
    private readonly now: Clock = systemClock,
  ) {}

  /** すべての請求書を走査し、期限超過を OVERDUE へ更新して初回のみ未払い通知する */
  async sweep(): Promise<void> {
    const now = this.now();
    const all = await this.invoices.findAll();
    for (const invoice of all) {
      if (!invoice.markOverdue(now)) {
        continue;
      }
      await this.invoices.update(invoice);
      try {
        await this.notifier.notify({
          bookingId: invoice.cargoBookingId.value,
          notificationType: NotificationType.PAYMENT_OVERDUE,
          recipient: ACCOUNTING_RECIPIENT,
          body: this.buildOverdueBody(invoice.invoiceNumber, invoice.dueDate, invoice.finalAmount.amount.toNumber(), invoice.finalAmount.currency),
        });
      } catch (error) {
        this.logger.error(`未払い通知に失敗（${invoice.invoiceNumber}）: ${String(error)}`);
      }
    }
  }

  private buildOverdueBody(invoiceNumber: string, dueDate: Date, amount: number, currency: string): string {
    const due = dueDate.toISOString().slice(0, 10);
    return `請求書 ${invoiceNumber} が支払期限（${due}）を超過しました。請求金額: ${amount.toLocaleString()} ${currency}`;
  }
}
