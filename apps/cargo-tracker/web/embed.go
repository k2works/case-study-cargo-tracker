// Package web は html/template テンプレートと静的資産を embed.FS で同梱する。
package web

import "embed"

// TemplatesFS はテンプレート一式を同梱した FS。
//
//go:embed templates
var TemplatesFS embed.FS

// StaticFS は静的資産（CSS/JS）を同梱した FS。
//
//go:embed static
var StaticFS embed.FS

// Templates は同梱テンプレートの FS を返す（レンダラへ注入する）。
func Templates() embed.FS { return TemplatesFS }
