import { kw, hz } from '../lib/format'

// Two small system-wide history charts for the Grid overview -- plain inline SVG, the same
// approach PlantView's own recent-output/forecast sparklines already use, just with two series
// instead of one and a couple of axis labels. Not worth a charting dependency for two polylines.

const WIDTH = 460
const HEIGHT = 110

/** @param series [{ values, color }, ...] -- all drawn against one shared y-scale so two lines on
 *  the same chart are honestly comparable, not each stretched to fill the frame on its own. */
function polylines(series, min, max) {
  const span = Math.max(1e-9, max - min)
  return series.map(({ values, color }) => {
    if (values.length < 2) return null
    const points = values.map((v, i) => {
      const x = (i / (values.length - 1)) * WIDTH
      const y = HEIGHT - ((v - min) / span) * (HEIGHT - 4) - 2
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    return <polyline key={color} points={points.join(' ')} fill="none" stroke={color} strokeWidth="1.75" vectorEffect="non-scaling-stroke" />
  })
}

function ChartFrame({ title, legend, children, footer }) {
  return (
    <div>
      <div className="flex items-center justify-between">
        <p className="text-[10px] font-medium text-slate-500">{title}</p>
        <div className="flex gap-2.5">
          {legend.map((l) => (
            <span key={l.label} className="flex items-center gap-1 text-[9px] text-slate-500">
              <span className="inline-block h-1.5 w-1.5 rounded-full" style={{ background: l.color }} />
              {l.label}
            </span>
          ))}
        </div>
      </div>
      <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} preserveAspectRatio="none" className="mt-1.5 h-24 w-full">
        {children}
      </svg>
      {footer && <div className="mt-1 flex justify-between text-[9px] text-slate-400">{footer}</div>}
    </div>
  )
}

/** Supply and demand over the same window, one shared kW scale -- the two lines crossing is
 *  exactly the moments {@code loadExceeded} flips, visible without a separate indicator for it. */
function SupplyDemandChart({ points }) {
  const supply = points.map((p) => p.totalSupplyKw)
  const demand = points.map((p) => p.totalDemandKw)
  const min = Math.min(...supply, ...demand)
  const max = Math.max(...supply, ...demand, min + 1)

  return (
    <ChartFrame
      title="Supply vs demand"
      legend={[{ label: 'Supply', color: '#0ea5e9' }, { label: 'Demand', color: '#f97316' }]}
      footer={<><span>{kw(min)}</span><span>{kw(max)}</span></>}
    >
      {polylines([{ values: supply, color: '#0ea5e9' }, { values: demand, color: '#f97316' }], min, max)}
    </ChartFrame>
  )
}

/** Zero-centered, and never scaled in tighter than the ±0.25 Hz clamp FrequencyController itself
 *  enforces -- a chart that zoomed in on a calm stretch would make ordinary noise look dramatic. */
function FrequencyChart({ points }) {
  const values = points.map((p) => p.frequencyDeviation)
  const bound = Math.max(0.25, ...values.map(Math.abs))
  const zeroY = HEIGHT - ((0 - -bound) / (2 * bound)) * (HEIGHT - 4) - 2

  return (
    <ChartFrame
      title="Frequency deviation"
      legend={[{ label: 'Hz', color: '#7c3aed' }]}
      footer={<><span>{hz(-bound)}</span><span>{hz(bound)}</span></>}
    >
      <line x1="0" y1={zeroY} x2={WIDTH} y2={zeroY} stroke="#e2e8f0" strokeWidth="1" strokeDasharray="3 3" />
      {polylines([{ values, color: '#7c3aed' }], -bound, bound)}
    </ChartFrame>
  )
}

/** `points` is Grid's own history endpoint response, oldest first -- see lib/gridApi.js. Renders
 *  nothing (not an empty frame) until there are at least two points to draw a line between. */
export default function HistoryCharts({ points }) {
  if (!points || points.length < 2) {
    return <p className="text-xs text-slate-400">Not enough history yet.</p>
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <SupplyDemandChart points={points} />
      <FrequencyChart points={points} />
    </div>
  )
}
