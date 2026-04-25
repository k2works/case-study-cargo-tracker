import { Routes, Route } from 'react-router'
import { AppLayout } from './layouts/AppLayout'

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<div className="p-8"><h1 className="text-2xl font-bold">Dashboard</h1><p className="mt-2 text-gray-600">国際貨物輸送管理システム</p></div>} />
      </Route>
    </Routes>
  )
}
