import { Link, Outlet } from 'react-router-dom'

/** Base page chrome: header nav plus an `Outlet` for the active route. */
export function AppLayout() {
  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <nav className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <Link to="/" className="text-lg font-bold">Acme Bank</Link>
          <div className="flex gap-4 text-sm">
            <Link to="/dashboard">Dashboard</Link>
            <Link to="/banker">Banker</Link>
            <Link to="/admin">Admin</Link>
          </div>
        </nav>
      </header>
      <main className="mx-auto max-w-6xl px-6 py-8">
        <Outlet />
      </main>
    </div>
  )
}
