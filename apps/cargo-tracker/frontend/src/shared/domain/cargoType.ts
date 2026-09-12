/**
 * 貨物種別（domain-model.md「ユビキタス言語」）。
 *
 * <p><b>共有に置く。</b> 予約（S21）だけでなく見積（S12）も同じ種別を扱う。
 * 機能ごとに型を持つと、種別を足したときに片方だけが直る。</p>
 *
 * <p><b>Routing は冷凍を {@code REEFER} と呼ぶ。</b> 呼び名が違うので、
 * 経路設計の画面へ遷移するときは翻訳する（{@code RoutingWorklistPage}）。
 * バックエンドでは契約の受け側が翻訳する。</p>
 */
export type CargoType = 'GENERAL' | 'HAZARDOUS' | 'REFRIGERATED';
