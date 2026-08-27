import { Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth } from './components/require-auth'
import { AppLayout } from './layouts/app-layout'
import { CancellationsPage } from './pages/cancellations-page'
import { CustomsDetailPage } from './pages/customs-detail-page'
import { CustomsNewPage } from './pages/customs-new-page'
import { AwaitingDischargePage } from './pages/awaiting-discharge-page'
import { BillingPage } from './pages/billing-page';
import { BillingNewPage } from './pages/billing-new-page';
import { EstimateDetailPage } from './pages/estimate-detail-page';
import { EstimateListPage } from './pages/estimate-list-page';
import { EstimateNewPage } from './pages/estimate-new-page';
import { PaymentConfirmPage } from './pages/payment-confirm-page';
import { InvoiceDetailPage } from './pages/invoice-detail-page';
import { CustomsPage } from './pages/customs-page'
import { DashboardPage } from './pages/dashboard-page'
import { ForbiddenPage } from './pages/forbidden-page'
import { LoginPage } from './pages/login-page'
import { PortalPage } from './pages/portal-page'
import { TrackingLookupPage } from './pages/tracking-lookup-page'
import { BookingListPage } from './pages/booking-list-page'
import { BookingDetailPage } from './pages/booking-detail-page'
import { BookingRegisterPage } from './pages/booking-register-page'
import { ShipperListPage } from './pages/shipper-list-page'
import { ShipperEditPage } from './pages/shipper-edit-page'
import { ShipperRegisterPage } from './pages/shipper-register-page'
import { VoyageListPage } from './pages/voyage-list-page'
import { VoyageDetailPage } from './pages/voyage-detail-page'
import { VoyageRegisterPage } from './pages/voyage-register-page'
import { RouteDesignPage } from './pages/route-design-page'
import { LockedAccountsPage } from './pages/locked-accounts-page'
import { HandlingPage } from './pages/handling-page'
import { TrackingManagePage } from './pages/tracking-manage-page'
import { TrackingExceptionsPage } from './pages/tracking-exceptions-page'
import { ShipperTrackingPage } from './pages/shipper-tracking-page'

export default function App() {
  return (
    <Routes>
      {/* 認証の外に置く入口。ここが無いと、未ログインの人はどこにも入れない */}
      <Route path="/" element={<PortalPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/403" element={<ForbiddenPage />} />
      {/* 公開の追跡照会（US18-5）。**認証の外に置く**——荷主はログインしない。
          番号なしの `/tracking` は入力欄だけを出す入口で、業務利用者もここから引ける
          （ui_design.md は全ロールの導線として定義している） */}
      <Route path="/tracking" element={<TrackingLookupPage />} />
      <Route path="/tracking/:trackingNumber" element={<TrackingLookupPage />} />

      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/dashboard" element={<DashboardPage />} />
      </Route>

      {/* 荷主の登録・検索は営業担当者の業務。担当外は 403 へ送る */}
      <Route
        element={
          <RequireAuth allowedRoles={['ROLE_SALES']}>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/booking/shippers" element={<ShipperListPage />} />
        <Route path="/booking/shippers/new" element={<ShipperRegisterPage />} />
        <Route path="/booking/shippers/:id/edit" element={<ShipperEditPage />} />
        {/* 貨物予約も営業担当者の業務。ROLE_SHIPPER には開かない（ADR-008）。
            US33 で開くのは自社貨物の追跡であり、予約登録・参照ではないため */}
        <Route path="/booking/new" element={<BookingRegisterPage />} />
        {/* 輸送見積（US01）。**営業担当者だけ**——荷主に「いくらで何日か」を
            答えるのは営業の仕事である */}
        <Route path="/booking/estimates" element={<EstimateListPage />} />
        <Route path="/booking/estimates/new" element={<EstimateNewPage />} />
        <Route path="/booking/estimates/:estimateId" element={<EstimateDetailPage />} />
      </Route>

      {/* ロックの解除はシステム管理者だけ（US32-4）。他のロールに開くと、
          誰でも他人のロックを外せることになり、アカウント保護（US31）が意味を失う */}
      {/* 精算は経理担当者の業務（US21・US22）。**営業や経路設計者には開かない**
          ——請求の金額を決めるのは経理であり、職掌が違う。サーバも同じ規則を持つ
          （画面に出す・出さないでは守れない） */}
      <Route
        element={
          <RequireAuth allowedRoles={['ROLE_ACCOUNTANT']}>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/billing" element={<BillingPage />} />
        <Route path="/billing/new/:bookingId" element={<BillingNewPage />} />
        <Route path="/billing/:invoiceId" element={<InvoiceDetailPage />} />
        {/* 入金の確認（US23-3）。**経理担当者だけ**——営業が入金を確認することはない */}
        <Route path="/billing/:invoiceId/payment" element={<PaymentConfirmPage />} />
      </Route>

      <Route
        element={
          <RequireAuth allowedRoles={['ROLE_ADMIN']}>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/admin/accounts" element={<LockedAccountsPage />} />
      </Route>

      {/* 荷主向けの自社貨物追跡（US33）。公開追跡とは分け、自社境界はサーバでも守る。 */}
      <Route
        element={
          <RequireAuth allowedRoles={['ROLE_SHIPPER']}>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/shipper/tracking" element={<ShipperTrackingPage />} />
        <Route path="/shipper/tracking/:trackingNumber" element={<ShipperTrackingPage />} />
      </Route>


      {/* 荷役の記録は荷役作業員の業務（[ADR-008]）。追跡管理者にも開くのは**参照だけ**で、
          記録はサーバが荷役作業員に限る。追跡は結果を見る役割であり、
          記録できると「見ている人が動かす」ことになる */}
      <Route
        element={
          <RequireAuth allowedRoles={['ROLE_HANDLER', 'ROLE_TRACKER']}>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/handling" element={<HandlingPage />} />
      </Route>

      {/* 貨物状態の管理は追跡管理者の業務（US17・US19・US20）。
          **例外の起票は荷役作業員にも開く**——破損・紛失に最初に気づくのは港にいる人で
          ある（US20 のアクターは 2 つ）。状態を動かせるのは追跡管理者だけで、それは
          サーバが決める（画面に出す・出さないでは守れない） */}
      <Route
        element={
          <RequireAuth allowedRoles={['ROLE_TRACKER', 'ROLE_HANDLER']}>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/tracking/manage" element={<TrackingManagePage />} />
      </Route>

      {/* 未解決の例外は**営業にも読ませる**（IT9 返済枠 0.9）。荷主は公開の追跡照会で
          「ご依頼元の営業担当へ」と案内されるのに、営業には気づく手段が無く、電話を
          受けてから追跡管理者を探すことになっていた。読むだけであり、起票も解決も
          できない（それはサーバが決める） */}
      <Route
        element={
          <RequireAuth allowedRoles={['ROLE_TRACKER', 'ROLE_HANDLER', 'ROLE_SALES']}>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/tracking/manage/exceptions" element={<TrackingExceptionsPage />} />
      </Route>

      {/* 輸送中のキャンセル承認は追跡管理者の業務（US30-4）。営業は申請する側であり、
          自分の申請を自分で承認できると承認の意味が無くなる */}
      <Route
        element={
          <RequireAuth allowedRoles={['ROLE_TRACKER']}>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/booking/cancellations" element={<CancellationsPage />} />
      </Route>

      {/* 通関管理は荷役作業員（申告の登録）と追跡管理者（状態の更新）の両方が使う。
          画面の中で操作を出し分ける——荷役作業員に「状態を更新する」を見せて 403 に
          しない（[ADR-025] 決定 6） */}
      <Route
        element={
          <RequireAuth allowedRoles={['ROLE_HANDLER', 'ROLE_TRACKER']}>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route
          path="/handling/awaiting-discharge"
          element={<AwaitingDischargePage />}
        />
        <Route path="/customs" element={<CustomsPage />} />
        {/* 状態を更新できるのは追跡管理者だけ。詳細は荷役作業員も読む
            ——自分が出した申告の行方を追えないと、引取の作業をいつ始められるか
            分からない。操作の出し分けは画面の中で行う */}
        <Route path="/customs/:declarationId" element={<CustomsDetailPage />} />
      </Route>

      {/* 申告を出すのは荷役作業員だけ（[ADR-025] 決定 6）。追跡管理者は状態を
          更新する側であり、申告そのものは出さない。サーバも同じ規則を持つ
          ——画面に出す・出さないでは守れない */}
      <Route
        element={
          <RequireAuth allowedRoles={['ROLE_HANDLER']}>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/customs/new" element={<CustomsNewPage />} />
      </Route>

      {/* 航海スケジュールの管理は経路設計者の業務。営業に開くと、営業が
          スケジュールと経路確定まで行えてしまい職掌分離が崩れる */}
      <Route
        element={
          <RequireAuth allowedRoles={['ROLE_ROUTING']}>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/routing/voyages" element={<VoyageListPage />} />
        <Route path="/routing/voyages/new" element={<VoyageRegisterPage />} />
        <Route path="/routing/voyages/:voyageNumber" element={<VoyageDetailPage />} />
        {/* 経路設計は予約を選ばないと開けない。サイドバーには置かず、
            入口は予約詳細の [経路を割り当て] とする（ui_design のナビゲーション表） */}
        <Route path="/routing/design/:bookingId" element={<RouteDesignPage />} />
      </Route>

      {/* 引き渡された予約は経路設計者も見る。中身が見えないと経路を組む判断ができない。
          ただし見える範囲はサーバが依頼済みだけに絞る（ADR-015） */}
      <Route
        element={
          <RequireAuth allowedRoles={['ROLE_SALES', 'ROLE_ROUTING']}>
            <AppLayout />
          </RequireAuth>
        }
      >
        {/* 一覧そのものは両者が開く。経路設計者に見えるのは依頼済みだけで、
            絞り込みの指定でその範囲は広げられない（ADR-015・サーバ側で担保） */}
        <Route path="/booking" element={<BookingListPage />} />
      </Route>

      {/* **予約の詳細は追跡管理者・荷役も読む**（IT10 レビュー）。誤配に最初に気づくのも、
          キャンセルを承認するのも追跡管理者であり、例外一覧・承認一覧・陸揚げ待ちの
          いずれからもここへ渡る導線がある。**読むだけである**——操作は集約の述語と
          ロールで画面が出し分け、サーバも書き換えの入口を開いていない。
          **一覧は広げない**：1 件を辿ることと、営業の案件を横断して眺めることは別。
          **経理担当者も読む**（IT11 レビュー）——請求書詳細から予約番号を押す導線があり、
          荷主に「この請求はどの貨物か」を聞かれたときに辿る */}
      <Route
        element={
          <RequireAuth
            allowedRoles={[
              'ROLE_SALES',
              'ROLE_ROUTING',
              'ROLE_TRACKER',
              'ROLE_HANDLER',
              'ROLE_ACCOUNTANT',
            ]}
          >
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/booking/:bookingId" element={<BookingDetailPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
