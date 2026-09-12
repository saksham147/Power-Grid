import { NavLink, Outlet } from 'react-router-dom'
import { useStatus } from './lib/queries'
import { isUnreachable } from './lib/api'
import { Banner } from './components/ui'

const SERVICES = [
  { name: 'Producer', to: '/' },
  { name: 'Grid' },
  { name: 'Distributor' },
  { name: 'Customer' },
  { name: 'Billing' },
]

const TABS = [
  { to: '/', label: 'Simulation', end: true },
  { to: '/plants', label: 'Fleet' },
]

export default function App() {
  const { error } = useStatus()
  const unreachable = isUnreachable(error)

  return (
    <div className="min-h-screen bg-white text-slate-900 dark:bg-slate-950 dark:text-slate-100">
      <header className="border-b border-slate-200 dark:border-slate-800">
        <div className="mx-auto flex max-w-4xl flex-wrap items-center gap-x-6 gap-y-3 px-6 py-4">
          <span className="text-sm font-semibold tracking-tight">Power Grid</span>
          <nav className="flex flex-wrap items-center gap-1.5 text-xs">
            {SERVICES.map((s) =>
              s.to ? (
                <span
                  key={s.name}
                  className="rounded-full bg-slate-900 px-2.5 py-1 font-medium text-white dark:bg-slate-100 dark:text-slate-900"
                >
                  {s.name}
                </span>
              ) : (
                <span
                  key={s.name}
                  title="Not built yet — no endpoints to call"
                  className="cursor-default rounded-full border border-dashed border-slate-300 px-2.5 py-1 text-slate-400 dark:border-slate-700 dark:text-slate-600"
                >
                  {s.name}
                </span>
              ),
            )}
          </nav>
        </div>
      </header>

      <div className="mx-auto max-w-4xl px-6 py-8">
        <nav className="mb-6 flex gap-1 border-b border-slate-200 dark:border-slate-800">
          {TABS.map((t) => (
            <NavLink
              key={t.to}
              to={t.to}
              end={t.end}
              className={({ isActive }) =>
                `-mb-px border-b-2 px-3 py-2 text-sm font-medium transition ${
                  isActive
                    ? 'border-slate-900 dark:border-slate-100'
                    : 'border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-slate-100'
                }`
              }
            >
              {t.label}
            </NavLink>
          ))}
        </nav>

        {unreachable && (
          <div className="mb-5">
            <Banner tone="warn">
              Cannot reach the Producer service on <b>localhost:8090</b>. Start it with{' '}
              <code>./mvnw spring-boot:run</code> in <code>Producer/</code>.
            </Banner>
          </div>
        )}

        <Outlet />
      </div>
    </div>
  )
}
