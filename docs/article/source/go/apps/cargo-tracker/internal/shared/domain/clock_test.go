package domain_test

import (
	"testing"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
)

func TestFixedClock(t *testing.T) {
	fixed := time.Date(2026, 9, 1, 12, 0, 0, 0, time.UTC)
	var c shared.Clock = shared.FixedClock{Fixed: fixed}
	assert.Equal(t, fixed, c.Now())
}

func TestSystemClock(t *testing.T) {
	var c shared.Clock = shared.SystemClock{}
	before := time.Now()
	got := c.Now()
	assert.False(t, got.Before(before))
}
