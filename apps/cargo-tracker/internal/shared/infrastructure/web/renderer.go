package web

import (
	"bytes"
	"html/template"
	"io/fs"
	"net/http"
	"path"
	"strconv"
	"strings"
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

// templateFuncs はテンプレート共通のヘルパ関数を返す。
func templateFuncs() template.FuncMap {
	return template.FuncMap{
		"add":   func(a, b int) int { return a + b },
		"comma": comma,
	}
}

// comma は整数を 3 桁区切りの文字列にする（金額表示用）。
func comma(n int64) string {
	s := strconv.FormatInt(n, 10)
	neg := strings.HasPrefix(s, "-")
	if neg {
		s = s[1:]
	}
	var b strings.Builder
	for i, c := range s {
		if i > 0 && (len(s)-i)%3 == 0 {
			b.WriteByte(',')
		}
		b.WriteRune(c)
	}
	if neg {
		return "-" + b.String()
	}
	return b.String()
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
	r.render(w, req, page, data, "")
}

// RenderPageWithError はエラーメッセージ（フラッシュ）付きでページを描画する。
func (r *Renderer) RenderPageWithError(w http.ResponseWriter, req *http.Request, page string, data any, flashError string) {
	r.render(w, req, page, data, flashError)
}

func (r *Renderer) render(w http.ResponseWriter, req *http.Request, page string, data any, flashError string) {
	files := append([]string{}, r.layouts...)
	files = append(files, page)

	tmpl, err := template.New(path.Base(page)).Funcs(templateFuncs()).ParseFS(r.fsys, files...)
	if err != nil {
		http.Error(w, "template parse error: "+err.Error(), http.StatusInternalServerError)
		return
	}

	pd := PageData{
		CurrentUser: CurrentUserFrom(req.Context()),
		FlashError:  flashError,
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
