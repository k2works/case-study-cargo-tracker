package domain_test

import (
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewNotification(t *testing.T) {
	sc, _ := shared.NewShipperCode("SH01")
	sent := time.Date(2026, 9, 1, 12, 0, 0, 0, time.UTC)
	t.Run("有効な通知記録を生成できる", func(t *testing.T) {
		n, err := domain.NewNotification(sc, "確定経路: JPTYO→USLAX 直行 12日", sent)
		require.NoError(t, err)
		assert.Equal(t, "SH01", n.ShipperCode().Value())
		assert.Equal(t, sent, n.SentAt())
		assert.Contains(t, n.Summary(), "JPTYO")
	})
	t.Run("サマリが空はエラー", func(t *testing.T) {
		_, err := domain.NewNotification(sc, "  ", sent)
		require.ErrorIs(t, err, domain.ErrEmptyNotificationSummary)
	})
}

func TestCargoBuildRouteNotificationContent(t *testing.T) {
	it, err := domain.NewCargoItinerary([]domain.Leg{
		mustLeg(t, "V-LEG1", "JPTYO", "SGSIN", day(2026, 10, 1), day(2026, 10, 5)),
		mustLeg(t, "V-LEG2", "SGSIN", "USLAX", day(2026, 10, 6), day(2026, 10, 13)),
	})
	require.NoError(t, err)

	t.Run("確定経路から通知内容を組み立てる", func(t *testing.T) {
		c := routedCargo(t)
		require.NoError(t, c.AssignToRouting())
		require.NoError(t, c.AssignItinerary(it))
		content, err := c.BuildRouteNotificationContent()
		require.NoError(t, err)
		assert.Equal(t, "JPTYO", content.Origin)
		assert.Equal(t, "USLAX", content.Destination)
		require.Len(t, content.Waypoints, 1)
		assert.Equal(t, "SGSIN", content.Waypoints[0])
		assert.Equal(t, 12, content.TransitDays) // 10/1 → 10/13
		assert.Equal(t, day(2026, 10, 13), content.ArrivalDate)
		assert.EqualValues(t, 100000, content.AmountValue)
		assert.Equal(t, "JPY", content.AmountCurr)
	})
	t.Run("経路未確定はエラー", func(t *testing.T) {
		c := routedCargo(t) // NOT_ROUTED
		_, err := c.BuildRouteNotificationContent()
		require.ErrorIs(t, err, domain.ErrNotRoutedForNotification)
	})
}

func TestBuildRouteNotificationContent_TransitDaysTruncation(t *testing.T) {
	origin, _ := shared.NewLocation("JPTYO")
	dest, _ := shared.NewLocation("USLAX")
	// 10/1 06:00 → 10/13 18:00（12.5 日）→ 切り捨てで 12 日
	load := time.Date(2026, 10, 1, 6, 0, 0, 0, time.UTC)
	unload := time.Date(2026, 10, 13, 18, 0, 0, 0, time.UTC)
	leg, err := domain.NewLeg("V-DIRECT", origin, dest, load, unload)
	require.NoError(t, err)
	it, err := domain.NewCargoItinerary([]domain.Leg{leg})
	require.NoError(t, err)
	c := routedCargo(t)
	require.NoError(t, c.AssignToRouting())
	require.NoError(t, c.AssignItinerary(it))
	content, err := c.BuildRouteNotificationContent()
	require.NoError(t, err)
	assert.Equal(t, 12, content.TransitDays)
}
