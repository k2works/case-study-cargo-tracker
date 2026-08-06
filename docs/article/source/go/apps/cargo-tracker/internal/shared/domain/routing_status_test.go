package domain_test

import (
	"testing"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
)

func TestRoutingStatusJa(t *testing.T) {
	assert.Equal(t, "未経路", shared.RoutingStatusNotRouted.Ja())
	assert.Equal(t, "経路確定", shared.RoutingStatusRouted.Ja())
	assert.Equal(t, "経路不整合", shared.RoutingStatusMisrouted.Ja())
	assert.Equal(t, "UNKNOWN", shared.RoutingStatus("UNKNOWN").Ja())
}
