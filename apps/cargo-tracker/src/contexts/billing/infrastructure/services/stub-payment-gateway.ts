import { randomUUID } from 'node:crypto';
import { type Clock, systemClock } from '../../../../shared/infrastructure/clock/clock.js';
import type {
  PaymentConfirmation,
  PaymentGatewayPort,
} from '../../application/outboundservices/acl/payment-gateway-port.js';

/**
 * PaymentGatewayPort のスタブ実装（US23-3）。
 * 決済機関は未接続のため、照会に対して常に入金済み（銀行振込・取引参照を採番）を返す。
 * 実際の決済機関接続（HTTP 契約テストを含む）は運用フェーズで差し替える（ADR-007 スタブ ACL パターン）。
 */
export class StubPaymentGateway implements PaymentGatewayPort {
  constructor(private readonly now: Clock = systemClock) {}

  async confirmPayment(invoiceNumber: string, _amount: number): Promise<PaymentConfirmation | null> {
    return {
      paidAt: this.now(),
      method: 'BANK_TRANSFER',
      transactionReference: `TXN-${invoiceNumber}-${randomUUID().slice(0, 8).toUpperCase()}`,
    };
  }
}
