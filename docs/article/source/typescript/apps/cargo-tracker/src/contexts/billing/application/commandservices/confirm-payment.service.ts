import { Logger } from '@nestjs/common';
import type { InvoiceRepository } from '../../domain/repository/invoice-repository.js';
import type { PaymentGatewayPort } from '../outboundservices/acl/payment-gateway-port.js';
import { BillingValidationError } from '../../domain/model/billing-validation-error.js';
import { PaymentStatus } from '../../domain/model/payment-status.js';
import {
  PAYMENT_CONFIRMED_EVENT,
  type PaymentConfirmedPayload,
} from '../../../../shared/contracts/payment-confirmed.contract.js';

/** イベント発行ポート（コミット後に emit する。Billing Context ローカル定義） */
export interface EventPublisher {
  emit(event: string, payload: unknown): void;
}

/**
 * 入金確認ユースケース（US23-3/4）。
 * 決済機関（PaymentGatewayPort）で入金を照会し、入金済みなら請求書を精算済（CONFIRMED）へ遷移させ
 * 入金記録（決済手段・取引参照）を保存する。コミット後に精算完了イベントを発行し、
 * Booking 側で予約を SETTLED へ遷移させる（イベント + 冪等リスナー・ADR-005/009）。
 */
export class ConfirmPaymentService {
  private readonly logger = new Logger(ConfirmPaymentService.name);

  constructor(
    private readonly invoices: InvoiceRepository,
    private readonly gateway: PaymentGatewayPort,
    private readonly events: EventPublisher,
  ) {}

  async confirm(invoiceNumber: string): Promise<void> {
    const invoice = await this.invoices.findByInvoiceNumber(invoiceNumber);
    if (invoice === null) {
      throw new BillingValidationError(`請求書が見つかりません: ${invoiceNumber}`);
    }
    // 決済機関照会の前にドメイン状態を確認し、確定済み/返金済みは早期リターンする。
    // gateway 呼び出しより前に窓を閉じることで、二重確認・二重イベント発行を防ぐ（programmer#2）。
    if (
      invoice.paymentStatus === PaymentStatus.CONFIRMED ||
      invoice.paymentStatus === PaymentStatus.REFUNDED
    ) {
      this.logger.warn(`入金確認は不要（既に ${invoice.paymentStatus}）: ${invoiceNumber}`);
      return;
    }
    const confirmation = await this.gateway.confirmPayment(
      invoiceNumber,
      invoice.finalAmount.amount.toNumber(),
    );
    if (confirmation === null) {
      throw new BillingValidationError('入金が確認できませんでした。時間をおいて再度お試しください。');
    }

    invoice.confirmPayment(confirmation.paidAt, confirmation.method, confirmation.transactionReference);
    await this.invoices.update(invoice);

    // コミット後副作用（ADR-009）。SETTLED 反映は Booking 側リスナーへ委譲する（BC 独立性）。
    // 発行失敗でも入金確認自体は成立済みのため、ログのみで握る（要リカバリ）。
    try {
      const payload: PaymentConfirmedPayload = { bookingId: invoice.cargoBookingId.value };
      this.events.emit(PAYMENT_CONFIRMED_EVENT, payload);
    } catch (error) {
      this.logger.error(`精算完了イベントの発行に失敗（SETTLED 反映が保留）: ${String(error)}`);
    }
  }
}
