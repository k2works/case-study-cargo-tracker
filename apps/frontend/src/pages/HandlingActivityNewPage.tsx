import { HandlingActivityForm } from '../features/handling/components/HandlingActivityForm'

export function HandlingActivityNewPage() {
  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold mb-4">荷役作業記録</h1>
      <HandlingActivityForm />
    </div>
  )
}
