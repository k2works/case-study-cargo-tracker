import { useState } from 'react'
import { Link } from 'react-router'
import { VoyageList } from '../features/routing/components/VoyageList'
import { VoyageForm } from '../features/routing/components/VoyageForm'
import type { Voyage } from '../features/routing/types/voyage'

export function VoyageListPage() {
  const [editingVoyage, setEditingVoyage] = useState<Voyage | null>(null)

  return (
    <div>
      <h1>航海スケジュール管理</h1>
      <Link to="/voyages/new">新規登録</Link>

      {editingVoyage ? (
        <div>
          <h2>航海編集: {editingVoyage.voyageNumber}</h2>
          <VoyageForm
            voyage={editingVoyage}
            onSuccess={() => setEditingVoyage(null)}
          />
          <button onClick={() => setEditingVoyage(null)}>キャンセル</button>
        </div>
      ) : (
        <VoyageList onEdit={setEditingVoyage} />
      )}
    </div>
  )
}
