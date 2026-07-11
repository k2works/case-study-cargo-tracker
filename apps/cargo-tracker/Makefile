DB_URL ?= postgres://cargo:cargo@localhost:5432/cargo_tracker?sslmode=disable

.PHONY: help run tdd build test test-integration test-all lint arch check generate migrate-up migrate-down

help: ## タスク一覧を表示
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "  %-18s %s\n", $$1, $$2}'

run: ## 開発サーバーを起動
	go run ./cmd/server

tdd: ## テスト自動再実行（要 gow）
	gow test ./...

build: ## ビルド
	go build ./...

test: ## 単体テスト（カバレッジ付き）
	go test -cover ./...

test-integration: ## 統合テスト（testcontainers-go。Docker 必須）
	go test -tags=integration ./...

test-all: test test-integration ## 全テスト

lint: ## 静的解析（golangci-lint + govulncheck）
	golangci-lint run ./...
	govulncheck ./...

arch: ## アーキテクチャルール検証
	go-arch-lint check

check: build test lint arch ## すべてのテストと品質チェック

generate: ## sqlc コード生成
	sqlc generate

migrate-up: ## マイグレーション適用
	migrate -path db/migrations -database "$(DB_URL)" up

migrate-down: ## マイグレーションを 1 ステップ戻す
	migrate -path db/migrations -database "$(DB_URL)" down 1
