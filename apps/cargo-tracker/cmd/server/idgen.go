package main

import "github.com/google/uuid"

// uuidGenerator は UUID による ID 採番の本番実装。
// application.IDGenerator を満たす。
type uuidGenerator struct{}

// Generate は新しい UUID 文字列を返す。
func (uuidGenerator) Generate() string { return uuid.NewString() }
