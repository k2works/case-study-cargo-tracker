import type { ReactElement } from 'react';
import { Route, Routes } from 'react-router';
import { RequireRole } from '@/features/auth';
import { BusinessLayout } from '@/layouts/BusinessLayout';
import { BookingDetailPage, BookingNewPage, BookingsPage, ShipperDetailPage, ShipperNewPage, ShippersPage } from '@/pages/BookingPages';
import { Home } from '@/pages/Home';

/** 画面のルーティング。画面ごとに、ロールの表（features/auth/access）で許された場合だけ開く。 */
const guarded = (path: string, page: ReactElement) => <Route path={path} element={<RequireRole path={path}>{page}</RequireRole>} />;

export function AppRoutes() {
  return (
    <BusinessLayout>
      <Routes>
        {guarded('/', <Home />)}
        {guarded('/shippers', <ShippersPage />)}
        {guarded('/shippers/new', <ShipperNewPage />)}
        {guarded('/shippers/:id', <ShipperDetailPage />)}
        {guarded('/bookings', <BookingsPage />)}
        {guarded('/bookings/new', <BookingNewPage />)}
        {guarded('/bookings/:id', <BookingDetailPage />)}
        <Route path="*" element={<main><h1>見つかりません</h1></main>} />
      </Routes>
    </BusinessLayout>
  );
}
