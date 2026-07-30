import { Body, Controller, Get, Inject, Param, Post, Query, Req, Res, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { Decimal } from 'decimal.js';
import { renderPage } from '../../../views/render.js';
import { BillingIndex } from '../../../views/billing/Index.js';
import { BillingNew, type InvoiceDraft } from '../../../views/billing/New.js';
import { BillingShow } from '../../../views/billing/Show.js';
import { Role } from '../../../shared/domain/model/role.js';
import { RolesGuard } from '../../../shared/presentation/auth/roles.guard.js';
import { Roles } from '../../../shared/presentation/auth/roles.decorator.js';
import { BillingQueryService } from '../application/queryservices/billing-query.service.js';
import {
  GenerateInvoiceService,
  type InvoiceAdjustmentInput,
} from '../application/commandservices/generate-invoice.service.js';
import { ConfirmPaymentService } from '../application/commandservices/confirm-payment.service.js';
import { MarkOverdueService } from '../application/commandservices/mark-overdue.service.js';
import type { BillingSnapshotAcl } from '../application/outboundservices/acl/billing-snapshot-acl.js';
import type { BillingSnapshot } from '../domain/model/billing-snapshot.js';
import { FreightCalculator } from '../domain/model/freight-calculator.js';
import { Invoice } from '../domain/model/invoice.js';
import { BillingBookingId } from '../domain/model/billing-booking-id.js';
import { BillingShipperId } from '../domain/model/billing-shipper-id.js';
import { DiscountRate } from '../domain/model/discount-rate.js';
import { BillingValidationError } from '../domain/model/billing-validation-error.js';
import {
  BILLING_QUERY_SERVICE,
  BILLING_SNAPSHOT_ACL,
  CONFIRM_PAYMENT_SERVICE,
  GENERATE_INVOICE_SERVICE,
  MARK_OVERDUE_SERVICE,
} from '../billing.tokens.js';

/** フォーム POST ボディの値（未選択・単一・複数選択）。料金算出フォームの調整明細で配列になりうる */
type FormValue = string | string[] | undefined;

/**
 * 精算コントローラ（US21/US22/US23）。経理担当者（ROLE_BILLING）が
 * 請求書一覧・料金算出・発行・請求書詳細・入金確認を行う。
 */
@Controller('billing/invoices')
@UseGuards(RolesGuard)
export class BillingController {
  private readonly calculator = new FreightCalculator();

  constructor(
    @Inject(BILLING_QUERY_SERVICE) private readonly query: BillingQueryService,
    @Inject(GENERATE_INVOICE_SERVICE) private readonly generateService: GenerateInvoiceService,
    @Inject(BILLING_SNAPSHOT_ACL) private readonly snapshots: BillingSnapshotAcl,
    @Inject(CONFIRM_PAYMENT_SERVICE) private readonly confirmService: ConfirmPaymentService,
    @Inject(MARK_OVERDUE_SERVICE) private readonly overdueService: MarkOverdueService,
  ) {}

  @Get()
  @Roles(Role.BILLING)
  async index(@Req() req: Request, @Res() res: Response): Promise<void> {
    // 照会時に期限超過を判定して OVERDUE 更新 + 初回のみ未払い通知（バッチ不在のため。US23-5）
    await this.overdueService.sweep();
    const [unbilled, invoices] = await Promise.all([
      this.query.listUnbilledDeliveries(),
      this.query.listInvoices(),
    ]);
    const flash = req.session.flash ?? {};
    req.session.flash = {};
    renderPage(
      res,
      BillingIndex({ user: req.session.user!, unbilled, invoices, success: flash.success }),
    );
  }

  @Get('new')
  @Roles(Role.BILLING)
  async newForm(
    @Query('bookingId') bookingId: string | undefined,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const snapshot = bookingId ? await this.snapshots.findByBookingId(bookingId) : null;
    if (snapshot === null) {
      req.session.flash = { success: undefined };
      res.redirect('/billing/invoices');
      return;
    }
    renderPage(res, BillingNew({ user: req.session.user!, draft: this.toDraft(snapshot) }));
  }

  @Post()
  @Roles(Role.BILLING)
  async issue(
    @Body() body: Record<string, FormValue>,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const bookingId = typeof body.bookingId === 'string' ? body.bookingId : '';
    try {
      const invoiceNumber = await this.generateService.generate(bookingId, this.toAdjustments(body));
      req.session.flash = { success: `請求書 ${invoiceNumber} を発行しました（未払い）` };
      res.redirect('/billing/invoices');
    } catch (error) {
      const snapshot = bookingId ? await this.snapshots.findByBookingId(bookingId) : null;
      res.status(200);
      if (snapshot === null) {
        renderPage(
          res,
          BillingIndex({
            user: req.session.user!,
            unbilled: await this.query.listUnbilledDeliveries(),
            invoices: await this.query.listInvoices(),
            success: undefined,
          }),
        );
        return;
      }
      renderPage(
        res,
        BillingNew({
          user: req.session.user!,
          draft: this.toDraft(snapshot),
          error: this.toMessage(error),
        }),
      );
    }
  }

  @Get(':invoiceNumber')
  @Roles(Role.BILLING)
  async show(
    @Param('invoiceNumber') invoiceNumber: string,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    // 詳細照会時にも期限超過を判定する（一覧を経由しない直接遷移でも OVERDUE を反映。US23-5）
    await this.overdueService.sweep();
    const invoice = await this.query.getInvoiceDetail(invoiceNumber);
    if (invoice === null) {
      res.redirect('/billing/invoices');
      return;
    }
    const flash = req.session.flash ?? {};
    req.session.flash = {};
    renderPage(
      res,
      BillingShow({ user: req.session.user!, invoice, success: flash.success, error: flash.error }),
    );
  }

  @Post(':invoiceNumber/confirm')
  @Roles(Role.BILLING)
  async confirm(
    @Param('invoiceNumber') invoiceNumber: string,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    try {
      await this.confirmService.confirm(invoiceNumber);
      req.session.flash = { success: `請求書 ${invoiceNumber} の入金を確認しました（精算済）` };
    } catch (error) {
      req.session.flash = { success: undefined, error: this.toMessage(error) };
    }
    res.redirect(`/billing/invoices/${invoiceNumber}`);
  }

  /** フォームの並列配列（説明・金額・区分）を料金調整の入力へ変換する */
  private toAdjustments(body: Record<string, FormValue>): InvoiceAdjustmentInput[] {
    const descriptions = toArray(body['adjustmentDescription']);
    const amounts = toArray(body['adjustmentAmount']);
    const deductions = toArray(body['adjustmentDeduction']);
    const result: InvoiceAdjustmentInput[] = [];
    for (let i = 0; i < descriptions.length; i += 1) {
      const description = descriptions[i]?.trim() ?? '';
      const amountValue = Number(amounts[i] ?? '');
      if (description.length === 0 || !Number.isFinite(amountValue) || amountValue <= 0) {
        continue;
      }
      result.push({ description, amountValue, isDeduction: deductions[i] === 'true' });
    }
    return result;
  }

  /** スナップショットから料金プレビュー付きの表示モデルを組み立てる（ドメイン計算を再利用） */
  private toDraft(snapshot: BillingSnapshot): InvoiceDraft {
    const baseAmount = this.calculator.calculate({
      transitDays: snapshot.transitDays,
      weightKg: new Decimal(snapshot.weightKg),
      cargoType: snapshot.cargoType,
    });
    const preview = Invoice.issue({
      invoiceNumber: 'PREVIEW',
      cargoBookingId: BillingBookingId.of(snapshot.bookingId),
      shipperId: BillingShipperId.of(snapshot.shipperId, snapshot.shipperType),
      baseAmount,
      discountRate: DiscountRate.of(snapshot.discountRate),
      issuedAt: new Date(),
    });
    return {
      bookingId: snapshot.bookingId,
      shipperName: snapshot.shipperName,
      shipperType: snapshot.shipperType,
      isCorporate: snapshot.shipperType === 'CORPORATE',
      origin: snapshot.origin,
      destination: snapshot.destination,
      weightKg: snapshot.weightKg,
      cargoType: snapshot.cargoType,
      transitDays: snapshot.transitDays,
      baseAmountValue: baseAmount.amount.toNumber(),
      discountRatePercent: new Decimal(snapshot.discountRate).times(100).toString(),
      discountAmountValue: preview.discountAmount.amount.toNumber(),
      finalPreviewValue: preview.finalAmount.amount.toNumber(),
      currency: baseAmount.currency,
      activeExceptions: snapshot.activeExceptionTypes,
    };
  }

  private toMessage(error: unknown): string {
    if (error instanceof BillingValidationError) {
      return error.message;
    }
    return '請求書の発行に失敗しました。入力内容を確認してください。';
  }
}

function toArray(value: string | string[] | undefined): string[] {
  if (Array.isArray(value)) {
    return value;
  }
  if (typeof value === 'string') {
    return [value];
  }
  return [];
}
