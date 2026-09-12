const VARIANTS = {
  default:
    'border-slate-300 hover:bg-slate-100 dark:border-slate-700 dark:hover:bg-slate-800',
  primary:
    'border-slate-900 bg-slate-900 text-white hover:bg-slate-700 dark:border-slate-100 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white',
  danger:
    'border-red-300 text-red-700 hover:bg-red-50 dark:border-red-900/70 dark:text-red-400 dark:hover:bg-red-950/40',
}

export function Button({ variant = 'default', className = '', ...props }) {
  return (
    <button
      className={`rounded-lg border px-3 py-1.5 text-sm font-medium transition
        focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-500
        disabled:cursor-not-allowed disabled:opacity-40 ${VARIANTS[variant]} ${className}`}
      {...props}
    />
  )
}

export function Field({ label, hint, error, className = '', ...props }) {
  return (
    <label className={`block ${className}`}>
      <span className="block text-xs font-medium text-slate-600 dark:text-slate-400">{label}</span>
      <input
        className={`mt-1 w-full rounded-lg border bg-transparent px-2.5 py-1.5 text-sm tabular-nums
          focus:outline-2 focus:outline-offset-0 focus:outline-sky-500
          ${error ? 'border-red-400 dark:border-red-800' : 'border-slate-300 dark:border-slate-700'}`}
        {...props}
      />
      {error && <span className="mt-1 block text-xs text-red-600 dark:text-red-400">{error}</span>}
      {!error && hint && (
        <span className="mt-1 block text-xs text-slate-500">{hint}</span>
      )}
    </label>
  )
}

export function Card({ title, action, children, className = '' }) {
  return (
    <section
      className={`rounded-xl border border-slate-200 p-5 dark:border-slate-800 ${className}`}
    >
      {(title || action) && (
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="text-sm font-semibold tracking-tight">{title}</h2>
          {action}
        </div>
      )}
      {children}
    </section>
  )
}

/** Surfaces the backend's own message and per-field details, never a generic string. */
export function Banner({ error, tone = 'error', children }) {
  if (!error && !children) return null
  const tones = {
    error: 'border-red-300 bg-red-50 text-red-800 dark:border-red-900/70 dark:bg-red-950/30 dark:text-red-300',
    warn: 'border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-900/70 dark:bg-amber-950/30 dark:text-amber-300',
  }
  return (
    <div className={`rounded-lg border px-3 py-2 text-sm ${tones[tone]}`}>
      {error ? (
        <>
          <p>{error.message}</p>
          {error.details?.length > 0 && (
            <ul className="mt-1 list-disc pl-4 text-xs opacity-90">
              {error.details.map((d) => (
                <li key={d}>{d}</li>
              ))}
            </ul>
          )}
        </>
      ) : (
        children
      )}
    </div>
  )
}

export const Stat = ({ label, value, mono = true }) => (
  <div>
    <div className="text-xs text-slate-500">{label}</div>
    <div className={`text-sm font-medium ${mono ? 'tabular-nums' : ''}`}>{value}</div>
  </div>
)
