/**
 * 「元の画面に戻る」ためのパス。
 *
 * 経路設計から航海詳細へ入った人は、戻り先が「航海スケジュール一覧」だけだと、
 * どの予約を見ていたか分からない場所に放り出される。戻り先を URL で持ち回る。
 */

/**
 * 持ち回ってよい戻り先か。
 *
 * <p>URL の値をそのまま遷移先にすると、外部のアドレスを差し込まれる余地ができる。
 * 受け入れるのはこのアプリ内の絶対パスだけとする。2 文字目が `/` または `\` のものは
 * 別ホストへの相対 URL として解釈されうるため除く（ブラウザは `/\example.com` を
 * `//example.com` と同じに扱う）。
 */
export function safeReturnPath(value: string | null): string | null {
  if (value === null || !value.startsWith('/') || /^\/[/\\]/.test(value)) {
    return null
  }
  return value
}

/** いまの画面（条件付き）を戻り先として渡すための検索文字列を作る。 */
export function withReturnTo(path: string, returnTo: string): string {
  return `${path}?from=${encodeURIComponent(returnTo)}`
}
