package application

import (
	"context"
	"errors"
	"log/slog"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ユースケースエラー。
var (
	ErrCargoNotClaimed = errors.New("cargo is not claimed; billing requires CLAIMED status")
	ErrAlreadyInvoiced = errors.New("invoice already exists for this booking")
	ErrInvoiceNotFound = errors.New("invoice not found")
)

const (
	taxRate = 0.10
	dueDays = 30
)

// GenerateInvoiceService は輸送料金算出・法人割引・精算書発行のユースケース（US21/US22）。
type GenerateInvoiceService struct {
	repo     InvoiceRepository
	cargo    CargoBillingSnapshotProvider
	shipper  ShipperContractProvider
	issuer   InvoiceNumberIssuer
	notifier NotificationPort
	clock    shared.Clock
	policy   domain.DiscountPolicy
}

// NewGenerateInvoiceService は GenerateInvoiceService を生成する。
func NewGenerateInvoiceService(repo InvoiceRepository, cargo CargoBillingSnapshotProvider, shipper ShipperContractProvider, issuer InvoiceNumberIssuer, notifier NotificationPort, clock shared.Clock) *GenerateInvoiceService {
	return &GenerateInvoiceService{repo: repo, cargo: cargo, shipper: shipper, issuer: issuer, notifier: notifier, clock: clock}
}

// GenerateInvoiceCommand は精算書発行コマンド（US21/US22）。
type GenerateInvoiceCommand struct {
	BookingID string
}

// Generate は引取済み予約の料金を算出し、法人割引を適用して精算書を発行する。
func (s *GenerateInvoiceService) Generate(ctx context.Context, cmd GenerateInvoiceCommand) (string, error) {
	exists, err := s.repo.ExistsByBookingID(ctx, cmd.BookingID)
	if err != nil {
		return "", err
	}
	if exists {
		return "", ErrAlreadyInvoiced
	}

	snap, err := s.cargo.FetchBillingSnapshot(ctx, cmd.BookingID)
	if err != nil {
		return "", err
	}
	if snap.TransportStatus != string(shared.TransportStatusClaimed) {
		return "", ErrCargoNotClaimed
	}
	cargoType, err := shared.ParseCargoType(snap.CargoType)
	if err != nil {
		return "", err
	}

	shipperId, rate, err := s.resolveDiscount(ctx, snap.ShipperCode)
	if err != nil {
		return "", err
	}

	number, err := s.buildAndSave(ctx, cmd.BookingID, shipperId, rate,
		domain.CalculateBaseAmount(snap.DistanceFactor, snap.WeightKg, cargoType))
	if err != nil {
		return "", err
	}
	s.notify(ctx, snap.ShipperCode, "精算書を発行しました: "+number)
	return number, nil
}

// buildAndSave は請求番号を採番し、請求書を生成して永続化する。
func (s *GenerateInvoiceService) buildAndSave(ctx context.Context, bookingID string, shipperId domain.BillingShipperId, rate domain.DiscountRate, baseAmount domain.Money) (string, error) {
	number, err := s.issuer.Next(ctx, s.clock.Now())
	if err != nil {
		return "", err
	}
	invoiceId, err := domain.NewInvoiceId(number)
	if err != nil {
		return "", err
	}
	invoice, err := domain.GenerateInvoice(domain.GenerateInvoiceParams{
		InvoiceId:    invoiceId,
		BookingId:    domain.NewBillingBookingId(bookingID),
		ShipperId:    shipperId,
		BaseAmount:   baseAmount,
		DiscountRate: rate,
		TaxRate:      taxRate,
		IssuedAt:     s.clock.Now(),
		DueDays:      dueDays,
	})
	if err != nil {
		return "", err
	}
	if err := s.repo.Save(ctx, invoice); err != nil {
		return "", err
	}
	return number, nil
}

// resolveDiscount は Shipper ACL から契約割引率を取得し、DiscountPolicy で適用率を決定する（US22）。
func (s *GenerateInvoiceService) resolveDiscount(ctx context.Context, shipperCode string) (domain.BillingShipperId, domain.DiscountRate, error) {
	contract, err := s.shipper.FetchContract(ctx, shipperCode)
	if err != nil {
		return domain.BillingShipperId{}, domain.DiscountRate{}, err
	}
	contractRate, err := domain.NewDiscountRate(contract.DiscountRate)
	if err != nil {
		return domain.BillingShipperId{}, domain.DiscountRate{}, err
	}
	shipperId := domain.NewBillingShipperId(shipperCode, contract.ShipperType)
	return shipperId, s.policy.CalculateRate(shipperId, contractRate), nil
}

func (s *GenerateInvoiceService) notify(ctx context.Context, shipperCode, summary string) {
	if err := s.notifier.NotifyShipper(ctx, shipperCode, summary); err != nil {
		slog.WarnContext(ctx, "notify shipper failed", "shipperCode", shipperCode, "error", err)
	}
}

// ConfirmPaymentService は入金確認・精算完了のユースケース（US23）。
type ConfirmPaymentService struct {
	repo     InvoiceRepository
	settler  BookingSettler
	notifier NotificationPort
	clock    shared.Clock
}

// NewConfirmPaymentService は ConfirmPaymentService を生成する。
func NewConfirmPaymentService(repo InvoiceRepository, settler BookingSettler, notifier NotificationPort, clock shared.Clock) *ConfirmPaymentService {
	return &ConfirmPaymentService{repo: repo, settler: settler, notifier: notifier, clock: clock}
}

// Confirm は入金を確認し、請求を精算済み（CONFIRMED）にして予約を SETTLED にする。
func (s *ConfirmPaymentService) Confirm(ctx context.Context, invoiceNumber string) error {
	invoice, err := s.repo.FindByInvoiceNumber(ctx, invoiceNumber)
	if err != nil {
		return err
	}
	if err := invoice.ConfirmPayment(s.clock.Now()); err != nil {
		return err
	}
	if err := s.repo.Save(ctx, invoice); err != nil {
		return err
	}
	if err := s.settler.MarkSettled(ctx, invoice.BookingId().Value()); err != nil {
		return err
	}
	if err := s.notifier.NotifyShipper(ctx, invoice.ShipperId().ShipperCode(), "入金を確認しました。精算が完了しました。"); err != nil {
		slog.WarnContext(ctx, "notify shipper failed", "error", err)
	}
	return nil
}

// MarkOverdueService は支払期限超過の請求を OVERDUE にし経理担当者へ未払い通知する（US23）。
type MarkOverdueService struct {
	repo     InvoiceRepository
	notifier NotificationPort
	clock    shared.Clock
}

// NewMarkOverdueService は MarkOverdueService を生成する。
func NewMarkOverdueService(repo InvoiceRepository, notifier NotificationPort, clock shared.Clock) *MarkOverdueService {
	return &MarkOverdueService{repo: repo, notifier: notifier, clock: clock}
}

// MarkOverdue は指定請求書の期限超過を判定し、超過なら OVERDUE にして未払い通知する。
func (s *MarkOverdueService) MarkOverdue(ctx context.Context, invoiceNumber string) error {
	invoice, err := s.repo.FindByInvoiceNumber(ctx, invoiceNumber)
	if err != nil {
		return err
	}
	before := invoice.PaymentStatus()
	if err := invoice.MarkOverdue(s.clock.Now()); err != nil {
		return err
	}
	if invoice.PaymentStatus() == domain.PaymentStatusOverdue && before != domain.PaymentStatusOverdue {
		if err := s.repo.Save(ctx, invoice); err != nil {
			return err
		}
		if err := s.notifier.NotifyAccountant(ctx, invoiceNumber, "支払期限を超過しました。督促してください。"); err != nil {
			slog.WarnContext(ctx, "notify accountant failed", "error", err)
		}
	}
	return nil
}
