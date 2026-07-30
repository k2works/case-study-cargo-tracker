import { Decimal } from 'decimal.js';
import { InvoiceId } from './invoice-id.js';
import { BillingBookingId } from './billing-booking-id.js';
import { BillingShipperId } from './billing-shipper-id.js';
import { Money } from './money.js';
import { DiscountRate } from './discount-rate.js';
import { InvoiceLineItem } from './invoice-line-item.js';
import { PaymentStatus } from './payment-status.js';
import { BillingValidationError } from './billing-validation-error.js';

/** 既定の消費税率（10%） */
const DEFAULT_TAX_RATE = new Decimal('0.10');
/** 支払期限（発行日からの日数） */
const DUE_DAYS = 30;

interface IssueParams {
  invoiceId?: InvoiceId;
  invoiceNumber: string;
  cargoBookingId: BillingBookingId;
  shipperId: BillingShipperId;
  baseAmount: Money;
  /** 例外調整など、基本料金に加算する明細（補償費用など。省略時は空） */
  adjustments?: InvoiceLineItem[];
  /** 減額調整の明細（破損減額など。基本料金から控除する。省略時は空） */
  deductions?: InvoiceLineItem[];
  discountRate: DiscountRate;
  taxRate?: Decimal;
  issuedAt: Date;
}

interface ReconstructParams {
  invoiceId: InvoiceId;
  invoiceNumber: string;
  cargoBookingId: BillingBookingId;
  shipperId: BillingShipperId;
  baseAmount: Money;
  discountRate: DiscountRate;
  taxRate: Decimal;
  finalAmount: Money;
  taxAmount: Money;
  paymentStatus: PaymentStatus;
  issuedAt: Date;
  dueDate: Date;
  paidAt: Date | null;
  /** 永続化済みの明細（発行時に確定した内訳。復元時は再計算せずそのまま採用する） */
  lineItems: InvoiceLineItem[];
}

/**
 * 請求書集約ルート（Billing Context）。貨物輸送 1 件に対する請求書の発行・料金確定・支払い管理を統括する。
 * ビジネスルール（domain-model）:
 * - 法人荷主には最大 30% の割引を適用（個人は割引なし）
 * - 支払期限（発行日 + 30 日）超過で OVERDUE に遷移
 * - 支払い確定（CONFIRMED）後は再確認不可（返金は REFUNDED、本 IT スコープ外）
 */
export class Invoice {
  private _lineItems: InvoiceLineItem[];

  private constructor(
    readonly invoiceId: InvoiceId,
    readonly invoiceNumber: string,
    readonly cargoBookingId: BillingBookingId,
    readonly shipperId: BillingShipperId,
    readonly baseAmount: Money,
    private readonly adjustments: InvoiceLineItem[],
    private readonly deductions: InvoiceLineItem[],
    readonly discountRate: DiscountRate,
    readonly taxRate: Decimal,
    private _finalAmount: Money,
    private _taxAmount: Money,
    private _paymentStatus: PaymentStatus,
    readonly issuedAt: Date,
    readonly dueDate: Date,
    private _paidAt: Date | null,
  ) {
    this._lineItems = [];
  }

  get lineItems(): readonly InvoiceLineItem[] {
    return this._lineItems;
  }

  get finalAmount(): Money {
    return this._finalAmount;
  }

  get taxAmount(): Money {
    return this._taxAmount;
  }

  get paymentStatus(): PaymentStatus {
    return this._paymentStatus;
  }

  get paidAt(): Date | null {
    return this._paidAt;
  }

  /** 実際に適用された割引額（個人荷主は 0）。永続化・表示に用いる */
  get discountAmount(): Money {
    const effectiveRate = this.shipperId.isCorporate() ? this.discountRate : DiscountRate.zero();
    return effectiveRate.discountAmount(this.baseAmount);
  }

  /** 請求書を発行する（PENDING 状態で作成し、金額を確定する） */
  static issue(params: IssueParams): Invoice {
    const taxRate = params.taxRate ?? DEFAULT_TAX_RATE;
    const dueDate = new Date(params.issuedAt);
    dueDate.setUTCDate(dueDate.getUTCDate() + DUE_DAYS);
    const invoice = new Invoice(
      params.invoiceId ?? InvoiceId.generate(),
      params.invoiceNumber.trim(),
      params.cargoBookingId,
      params.shipperId,
      params.baseAmount,
      params.adjustments ?? [],
      params.deductions ?? [],
      params.discountRate,
      taxRate,
      Money.zero(params.baseAmount.currency),
      Money.zero(params.baseAmount.currency),
      PaymentStatus.PENDING,
      params.issuedAt,
      dueDate,
      null,
    );
    if (params.invoiceNumber.trim().length === 0) {
      throw new BillingValidationError('請求書番号は必須です');
    }
    invoice.calculateFinalAmount();
    return invoice;
  }

  static reconstruct(params: ReconstructParams): Invoice {
    const invoice = new Invoice(
      params.invoiceId,
      params.invoiceNumber,
      params.cargoBookingId,
      params.shipperId,
      params.baseAmount,
      // 復元後に calculateFinalAmount を再実行しない前提のため adjustments/deductions は保持不要。
      // 明細は永続化済みの内訳をそのまま採用する（集約状態の再導出禁止）。
      [],
      [],
      params.discountRate,
      params.taxRate,
      params.finalAmount,
      params.taxAmount,
      params.paymentStatus,
      params.issuedAt,
      params.dueDate,
      params.paidAt,
    );
    invoice._lineItems = params.lineItems;
    return invoice;
  }

  /**
   * 請求金額を確定する。
   * 請求金額 = (基本料金 + 加算調整 − 控除調整 − 割引) + 消費税
   *   割引 = 基本料金 × 割引率（個人荷主は 0%）
   *   消費税 = 割引後小計 × 税率
   * 算出根拠（基本料金・調整・減額・割引・消費税）は明細に積む。
   */
  calculateFinalAmount(): Money {
    // 個人荷主には割引を適用しない（domain-model ビジネスルール 2）
    const effectiveDiscount = this.shipperId.isCorporate() ? this.discountRate : DiscountRate.zero();

    let subtotal = this.baseAmount;
    for (const adjustment of this.adjustments) {
      subtotal = subtotal.add(adjustment.amount);
    }
    for (const deduction of this.deductions) {
      subtotal = subtotal.subtract(deduction.amount);
    }
    const discount = effectiveDiscount.discountAmount(this.baseAmount);
    const discountedSubtotal = subtotal.subtract(discount);
    const tax = discountedSubtotal.multiply(this.taxRate);

    this._taxAmount = tax;
    this._finalAmount = discountedSubtotal.add(tax);
    this.rebuildLineItems(effectiveDiscount, discount);
    return this._finalAmount;
  }

  /** 未払い/期限超過から入金確認済みへ遷移する */
  confirmPayment(paidAt: Date): void {
    if (this._paymentStatus === PaymentStatus.CONFIRMED) {
      throw new BillingValidationError('既に入金確認済みです');
    }
    if (this._paymentStatus === PaymentStatus.REFUNDED) {
      throw new BillingValidationError('返金済みの請求書は入金確認できません');
    }
    this._paymentStatus = PaymentStatus.CONFIRMED;
    this._paidAt = paidAt;
  }

  /**
   * 支払期限を超過していれば OVERDUE へ遷移する。
   * 初回遷移時のみ true（＝通知が必要）を返し、以降や非該当（CONFIRMED 等）は false を返す。
   */
  markOverdue(now: Date): boolean {
    if (this._paymentStatus !== PaymentStatus.PENDING) {
      return false;
    }
    if (now.getTime() <= this.dueDate.getTime()) {
      return false;
    }
    this._paymentStatus = PaymentStatus.OVERDUE;
    return true;
  }

  private rebuildLineItems(effectiveDiscount?: DiscountRate, discount?: Money): void {
    const rate = effectiveDiscount ?? (this.shipperId.isCorporate() ? this.discountRate : DiscountRate.zero());
    const discountAmount = discount ?? rate.discountAmount(this.baseAmount);

    const items: InvoiceLineItem[] = [InvoiceLineItem.of('基本料金', this.baseAmount)];
    items.push(...this.adjustments);
    // 控除明細は「減額（…）」として金額の大きさを表示する（Money は非負。控除は finalAmount 側で減算済み）
    for (const deduction of this.deductions) {
      items.push(InvoiceLineItem.of(`減額（${deduction.description}）`, deduction.amount));
    }
    if (discountAmount.amount.greaterThan(0)) {
      const afterDiscount = this.baseAmount.subtract(discountAmount);
      const ratePercent = rate.rate.times(100).toString();
      items.push(
        InvoiceLineItem.of(
          `法人割引（割引率 ${ratePercent}%・基本料金 ${this.baseAmount.amount.toString()} → 割引後 ${afterDiscount.amount.toString()}）`,
          discountAmount,
        ),
      );
    }
    const taxPercent = this.taxRate.times(100).toString();
    items.push(InvoiceLineItem.of(`消費税（${taxPercent}%）`, this._taxAmount));
    this._lineItems = items;
  }
}
