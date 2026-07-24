package web

import (
	"bytes"
	"html/template"
	"io/fs"
	"net/http"
	"path"
)

// Renderer は layout と各ページテンプレートを合成してレンダリングする。
type Renderer struct {
	fsys    fs.FS
	layouts []string
}

// NewRenderer はテンプレート FS からレンダラを生成する。
// layouts には全ページで共通利用するテンプレート（layout・fragments）を指定する。
func NewRenderer(fsys fs.FS, layouts ...string) *Renderer {
	return &Renderer{fsys: fsys, layouts: layouts}
}

// PageData はテンプレートへ渡す共通データ。
type PageData struct {
	CurrentUser CurrentUser
	Flash       string
	FlashError  string
	Data        any
}

// RenderPage は layout + 指定ページテンプレートを合成して HTTP レスポンスに書き出す。
func (r *Renderer) RenderPage(w http.ResponseWriter, req *http.Request, page string, data any) {
	files := append([]string{}, r.layouts...)
	files = append(files, page)

	tmpl, err := template.New(path.Base(page)).ParseFS(r.fsys, files...)
	if err != nil {
		http.Error(w, "template parse error: "+err.Error(), http.StatusInternalServerError)
		return
	}

	pd := PageData{
		CurrentUser: CurrentUserFrom(req.Context()),
		Data:        data,
	}

	var buf bytes.Buffer
	if err := tmpl.ExecuteTemplate(&buf, "layout", pd); err != nil {
		http.Error(w, "template render error: "+err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_, _ = buf.WriteTo(w)
}
