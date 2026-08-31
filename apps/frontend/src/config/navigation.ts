import type { Role } from "../types/role";

export type NavigationItem = {
  label: string;
  to: string;
  /** 表示するロール。空配列は全ロール（認証済み）を意味する。 */
  roles: Role[];
  /**
   * その画面が使えるか。
   *
   * 未実装の画面をリンクのまま出すと、押してもどこにも行けず「壊れている」と受け取られる。
   * 実装したイテレーションで true にする。
   */
  available: boolean;
};

/**
 * サイドバーのナビゲーション定義（ui_design.md のナビゲーション構成に一致させる）。
 *
 * 画面を作っても、そのロールがここから到達できなければ利用者の仕事は進まない。
 * 画面を追加したら必ずここにも足す。
 */
export const NAVIGATION: NavigationItem[] = [
  { label: "ダッシュボード", to: "/dashboard", roles: [], available: true },
  {
    label: "荷主管理",
    to: "/booking/shippers",
    roles: ["ROLE_SALES"],
    available: true,
  },
  {
    label: "見積管理",
    to: "/booking/estimates",
    roles: ["ROLE_SALES"],
    available: true,
  },
  // 予約管理は ROLE_SHIPPER には開かない（ADR-008）。US33 で利用者と荷主は紐付いたが、
  // 予約業務そのものを荷主へ開くストーリーではないため、追跡専用画面だけを開く。
  // **US18 では広げ直さない**（US18 は追跡番号だけで照会するため、紐付けのキーが要らない。
  // ADR-024 決定 10）。US33 の自社貨物追跡は専用画面で開く
  // 経路設計者にも開く。引き渡された予約へ行けないと、依頼に気づいても仕事が進まない。
  // 見える範囲は依頼済みだけで、絞り込みの指定では広げられない（ADR-015）
  {
    label: "貨物予約",
    to: "/booking",
    roles: ["ROLE_SALES", "ROLE_ROUTING"],
    available: true,
  },
  {
    label: "キャンセル承認",
    // 承認するのは追跡管理者だけ（US30-4）。申請するのは営業であり、
    // 自分の申請を自分で承認できると承認の意味が無くなる
    to: "/booking/cancellations",
    roles: ["ROLE_TRACKER"],
    available: true,
  },
  {
    label: "航海スケジュール",
    to: "/routing/voyages",
    roles: ["ROLE_ROUTING"],
    available: true,
  },
  // 経路設計（/routing/design/:bookingId）はサイドバーに置かない。
  // 予約を選ばないと開けない画面であり、メニューから踏むと予約番号の無い URL になる。
  // 入口は予約詳細の [経路を割り当て] で、経路設計者は「経路設計待ち」の予約一覧から辿る
  // 追跡照会は**認証の外**にある（US18-5）。ここに置くのは業務利用者のための入口で、
  // 荷主はポータルから入る。番号なしで開くと入力欄だけが出る
  { label: "貨物追跡", to: "/tracking", roles: [], available: true },
  {
    label: "自分の貨物",
    to: "/shipper/tracking",
    roles: ["ROLE_SHIPPER"],
    available: true,
  },
  {
    label: "貨物状態管理",
    // 起票は荷役作業員にも開く（US20 のアクターは 2 つ）。
    // **破損・紛失に最初に気づくのは港にいる人である**——追跡管理者だけに絞ると、
    // 気づいた人が伝える手段を持たない。状態を動かせるのは追跡管理者だけで、
    // それはサーバが決める（TrackingManagementController）
    to: "/tracking/manage",
    roles: ["ROLE_TRACKER", "ROLE_HANDLER"],
    available: true,
  },
  {
    // **状態軸の入口**（横断規約）。件数からではなく、メニューからも辿れるようにする。
    // 営業にも開く——荷主は公開照会で「ご依頼元の営業担当へ」と案内されるため、
    // 営業が何も知らないままでは案内が行き止まりになる（IT9 返済枠 0.9）
    label: "未解決の例外",
    to: "/tracking/manage/exceptions",
    roles: ["ROLE_TRACKER", "ROLE_HANDLER", "ROLE_SALES"],
    available: true,
  },
  // 記録できるのは荷役作業員だけだが、参照は追跡管理者にも開く（US15 の履歴）。
  // メニューに出すのは、そのロールで**何かできる**画面に限る
  {
    label: "荷役管理",
    to: "/handling",
    roles: ["ROLE_HANDLER", "ROLE_TRACKER"],
    available: true,
  },
  {
    label: "陸揚げ待ち",
    // 降ろすのは荷役の担当者、決めたのは追跡管理者。**作業指示は自動で作られない**
    // （[ADR-025] 決定 5）ため、荷役側にも入口が要る——連絡を待つだけだと、
    // 貨物は指定した港を通り過ぎる
    to: "/handling/awaiting-discharge",
    roles: ["ROLE_HANDLER", "ROLE_TRACKER"],
    available: true,
  },
  {
    label: "通関管理",
    // 申告の登録は荷役作業員、状態の更新は追跡管理者（[ADR-025] 決定 6）。
    // 一覧は両方が読む——荷役作業員は自分が出した申告の行方を追えないと、
    // 引取の作業をいつ始められるか分からない
    to: "/customs",
    roles: ["ROLE_HANDLER", "ROLE_TRACKER"],
    available: true,
  },
  {
    label: "精算管理",
    to: "/billing",
    roles: ["ROLE_ACCOUNTANT"],
    available: true,
  },
  // ロックされたアカウントの解除（US32）。**管理者以外には出さない**——出すと、
  // 押した先で 403 になる画面へ誘導することになる
  {
    label: "アカウント管理",
    to: "/admin/accounts",
    roles: ["ROLE_ADMIN"],
    available: true,
  },
  // 業務シミュレーション（US34・US35）。配備直後にどこが切れているかを、
  // 7 ロール分のログインをせずに切り分ける
  {
    label: "業務シミュレーション",
    to: "/admin/simulations",
    roles: ["ROLE_ADMIN"],
    available: true,
  },
];

/**
 * その URL を担当するメニューを返す。
 *
 * 前方一致で最初に当たったものを使ってはいけない。/booking/cancellations が
 * /booking に吸われ、準備中の画面が「使える」と判定される。最も長く一致した
 * ものが、その URL を担当するメニューである。
 *
 * この判定はダッシュボードと検査の両方から使う。別々に書くと、検査だけが
 * 正しく判定して本番の誤りを素通りさせる（IT2 で実際に起きた）。
 */
export function resolveNavigationItem(to: string): NavigationItem | undefined {
  return NAVIGATION.filter(
    (item) => item.to !== "/" && to.startsWith(item.to),
  ).sort((a, b) => b.to.length - a.to.length)[0];
}
