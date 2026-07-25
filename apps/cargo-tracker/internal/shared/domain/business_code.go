package domain

import "strings"

// GenerateBusinessCode は生の UUID から `<prefix>-` + 先頭 8 文字（大文字・ハイフン除去）の
// 業務識別コードを生成する。荷主コード（SHP-）・予約番号（BKG-）で共通に用いる（Rule of Three）。
func GenerateBusinessCode(prefix, rawUUID string) string {
	body := strings.ToUpper(strings.ReplaceAll(rawUUID, "-", ""))
	if len(body) > 8 {
		body = body[:8]
	}
	return prefix + "-" + body
}
