package domain_test

import (
	"testing"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

func TestGenerateBusinessCode(t *testing.T) {
	t.Run("プレフィックスと UUID 先頭 8 文字（大文字）で生成する", func(t *testing.T) {
		got := shared.GenerateBusinessCode("SHP", "abcdef12-3456-7890-abcd-ef1234567890")
		if got != "SHP-ABCDEF12" {
			t.Errorf("want SHP-ABCDEF12, got %s", got)
		}
	})

	t.Run("8 文字未満の入力はそのまま大文字化する", func(t *testing.T) {
		got := shared.GenerateBusinessCode("BKG", "ab12")
		if got != "BKG-AB12" {
			t.Errorf("want BKG-AB12, got %s", got)
		}
	})
}
