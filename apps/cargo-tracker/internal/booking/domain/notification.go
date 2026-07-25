package domain

import (
	"errors"
	"strings"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// 確定経路通知（US12）のドメインエラー。
var (
	ErrEmptyNotificationSummary = errors.New("notification summary must not be empty")
	ErrNotRoutedForNotification = errors.New("cargo must be routed before notifying the shipper")
)

// Notification は荷主への確定経路通知の送信記録を表す値オブジェクト（US12）。
// 荷主参照は BC 独立性のため業務識別子 ShipperCode で保持する。
type Notification struct {
	shipperCode shared.ShipperCode
	summary     string
	sentAt      time.Time
}

// NewNotification はバリデーション付きで通知記録を生成する。
func NewNotification(shipperCode shared.ShipperCode, summary string, sentAt time.Time) (Notification, error) {
	if strings.TrimSpace(summary) == "" {
		return Notification{}, ErrEmptyNotificationSummary
	}
	return Notification{shipperCode: shipperCode, summary: summary, sentAt: sentAt}, nil
}

// ShipperCode は宛先荷主コードを返す。
func (n Notification) ShipperCode() shared.ShipperCode { return n.shipperCode }

// Summary は通知内容サマリを返す。
func (n Notification) Summary() string { return n.summary }

// SentAt は送信日時を返す。
func (n Notification) SentAt() time.Time { return n.sentAt }

// RouteNotificationContent は確定経路の通知内容（US12: 経由港・所要日数・到着予定日・料金概算）。
type RouteNotificationContent struct {
	Origin      string
	Destination string
	Waypoints   []string
	TransitDays int
	ArrivalDate time.Time
	AmountValue int64
	AmountCurr  string
}

// BuildRouteNotificationContent は確定経路の Cargo から通知内容を組み立てる（US12）。
// 経路未確定（ROUTED でない・itinerary 無し）の場合はエラー。
func (c *Cargo) BuildRouteNotificationContent() (RouteNotificationContent, error) {
	if c.RoutingStatus() != shared.RoutingStatusRouted || c.itinerary == nil {
		return RouteNotificationContent{}, ErrNotRoutedForNotification
	}
	it := c.itinerary
	legs := it.Legs()
	waypoints := make([]string, 0, len(legs)-1)
	for i := 0; i < len(legs)-1; i++ {
		waypoints = append(waypoints, legs[i].UnloadLocation().UnLocode())
	}
	arrival := it.ExpectedArrivalTime()
	transitDays := int(arrival.Sub(legs[0].LoadTime()).Hours() / 24)
	return RouteNotificationContent{
		Origin:      it.Origin().UnLocode(),
		Destination: it.Destination().UnLocode(),
		Waypoints:   waypoints,
		TransitDays: transitDays,
		ArrivalDate: arrival,
		AmountValue: c.bookingAmount.Amount(),
		AmountCurr:  c.bookingAmount.Currency(),
	}, nil
}
