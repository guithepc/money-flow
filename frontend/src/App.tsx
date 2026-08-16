import { Sidebar } from '@/components/Sidebar'
import { Dashboard } from '@/pages/Dashboard'

export default function App() {
  return (
    <div className="flex h-screen bg-background text-fg">
      <Sidebar />
      <Dashboard />
    </div>
  )
}
