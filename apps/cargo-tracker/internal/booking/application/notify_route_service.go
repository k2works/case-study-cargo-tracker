package application

import (
	"context"
	"fmt"
	"strings"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// RouteNotificationView は確定経路通知のプレビュー用 DTO（US12）。
type RouteNotificationView struct {
	BookingID   string
	ShipperCode string
	Origin      string
	Destination string
	Waypoints   []string
	TransitDays int
	ArrivalDate string
	AmountValue int64
	AmountCurr  string
	Summary     string
}

// SentNotificationView は送信済み通知記録の表示用 DTO（US12）。
type SentNotificationView struct {
	Summary string
	SentAt  string
}

// NotifyRouteService は確定経路の荷主通知ユースケース（US12）。
type NotifyRouteService struct {
	repo         CargoItineraryRepository
	notifier     NotificationPort
	notification NotificationRepository
	clock        shared.Clock
}

// NewNotifyRouteService は NotifyRouteService を生成する。
func NewNotifyRouteService(repo CargoItineraryRepository, notifier NotificationPort, notification NotificationRepository, clock shared.Clock) *NotifyRouteService {
	return &NotifyRouteService{repo: repo, notifier: notifier, notification: notification, clock: clock}
}

// Preview は確定経路の通知内容（経由港・所要日数・到着予定日・料金概算）を返す（US12）。
func (s *NotifyRouteService) Preview(ctx context.Context, bookingID domain.BookingId) (RouteNotificationView, error) {
	cargo, err := s.repo.FindByBookingID(ctx, bookingID)
	if err != nil {
		return RouteNotificationView{}, err
	}
	content, err := cargo.BuildRouteNotificationContent()
	if err != nil {
		return RouteNotificationView{}, err
	}
	return toNotificationView(cargo, content), nil
}

// Notify は確定経路を荷主に通知し、送信記録を登録する（US12）。
// 注（既知の制約）: 送信（NotificationPort）→記録（NotificationRepository）は非トランザクションで、
// at-least-once セマンティクス。実 notifier（メール等）導入時は「送信成功・記録失敗→再送で二重通知」
// を避けるため outbox パターン等の冪等化を検討する。現状の loggingNotifier ではログ出力のみで実害はない。
func (s *NotifyRouteService) Notify(ctx context.Context, bookingID domain.BookingId) error {
	cargo, err := s.repo.FindByBookingID(ctx, bookingID)
	if err != nil {
		return err
	}
	content, err := cargo.BuildRouteNotificationContent()
	if err != nil {
		return err
	}
	summary := buildSummary(content)
	if err := s.notifier.Notify(ctx, cargo.ShipperCode(), summary); err != nil {
		return err
	}
	notification, err := domain.NewNotification(cargo.ShipperCode(), summary, s.clock.Now())
	if err != nil {
		return err
	}
	return s.notification.Save(ctx, bookingID, notification)
}

// SentNotifications は送信済み通知記録の一覧を返す（US12）。
func (s *NotifyRouteService) SentNotifications(ctx context.Context, bookingID domain.BookingId) ([]SentNotificationView, error) {
	records, err := s.notification.ListByBookingID(ctx, bookingID)
	if err != nil {
		return nil, err
	}
	views := make([]SentNotificationView, 0, len(records))
	for _, n := range records {
		views = append(views, SentNotificationView{
			Summary: n.Summary(),
			SentAt:  n.SentAt().Format("2006-01-02 15:04"),
		})
	}
	return views, nil
}

// toNotificationView は通知内容をプレビュー DTO へ変換する。
func toNotificationView(cargo *domain.Cargo, content domain.RouteNotificationContent) RouteNotificationView {
	return RouteNotificationView{
		BookingID:   cargo.BookingID().Value(),
		ShipperCode: cargo.ShipperCode().Value(),
		Origin:      content.Origin,
		Destination: content.Destination,
		Waypoints:   content.Waypoints,
		TransitDays: content.TransitDays,
		ArrivalDate: content.ArrivalDate.Format("2006-01-02"),
		AmountValue: content.AmountValue,
		AmountCurr:  content.AmountCurr,
		Summary:     buildSummary(content),
	}
}

// buildSummary は通知内容から荷主向けサマリ文字列を組み立てる。
func buildSummary(c domain.RouteNotificationContent) string {
	via := "直行"
	if len(c.Waypoints) > 0 {
		via = "経由: " + strings.Join(c.Waypoints, ", ")
	}
	return fmt.Sprintf("確定経路 %s→%s（%s）所要 %d 日・到着予定 %s・料金概算 %d %s",
		c.Origin, c.Destination, via, c.TransitDays, c.ArrivalDate.Format("2006-01-02"), c.AmountValue, c.AmountCurr)
}
