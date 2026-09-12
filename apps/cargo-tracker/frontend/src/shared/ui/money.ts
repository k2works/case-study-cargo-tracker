/**
 * 金額の表示。<b>画面はどこでも同じ書き方にする。</b>
 *
 * <p><b>共有に置く。</b> 金額を出す画面が請求（S60・S61・S62）だけでなく
 * 見積（S13）にも増えた。機能ごとに書式を持つと、同じ額が画面によって
 * 違う見た目になる——荷主から見れば「どちらが正しいのか」になる。</p>
 *
 * <p>割引のように「合計を減らす向き」の値は、呼ぶ側が負で渡して {@code −} を
 * 付ける。<b>金額そのものの符号で意味を変えない。</b></p>
 */
export function formatMoney(amount: number, currency: string): string {
  const formatted = new Intl.NumberFormat('ja-JP').format(Math.abs(amount));
  const sign = amount < 0 ? '− ' : '';
  const unit = currency === 'JPY' ? '¥' : currency;
  return `${sign}${unit} ${formatted}`;
}
