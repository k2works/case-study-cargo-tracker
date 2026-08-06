package domain_test

import (
	"testing"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

func TestParseCargoType(t *testing.T) {
	for _, v := range []string{"GENERAL", "HAZARDOUS", "REFRIGERATED"} {
		t.Run(v+" を解釈できる", func(t *testing.T) {
			ct, err := shared.ParseCargoType(v)
			if err != nil {
				t.Fatalf("予期しないエラー: %v", err)
			}
			if string(ct) != v {
				t.Errorf("want %s, got %s", v, ct)
			}
		})
	}

	t.Run("未知の種別はエラー", func(t *testing.T) {
		if _, err := shared.ParseCargoType("XXX"); err == nil {
			t.Error("未知の貨物種別はエラーになるべき")
		}
	})
}
