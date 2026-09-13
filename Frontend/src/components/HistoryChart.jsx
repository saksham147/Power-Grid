import { mw, time } from '../lib/format'

const STROKE = { THERMAL: 'stroke-orange-500', SOLAR: 'stroke-amber-400', WIND: 'stroke-teal-500' }
const FILL = { THERMAL: 'fill-orange-500/10', SOLAR: 'fill-amber-400/10', WIND: 'fill-teal-500/10' }
const DOT = { THERMAL: 'fill-orange-700 dark:fill-orange-300', SOLAR: 'fill-amber-700 dark:fill-amber-300', WIND: 'fill-teal-700 dark:fill-teal-300' }

const WIDTH = 640
const HEIGHT = 160
const PAD = { top: 8, right: 8, bottom: 18, left: 4 }

/**
 * A plant's output over time. No charting library — the day bar on the Simulation
 * screen already draws its own SVG, and one polyline needs nothing more.
 *
 * Points arrive oldest-first. A ROLLUP point (an hourly average, once its raw rows
 * have aged out) gets a dot; a RAW point does not, so the two resolutions are never
 * mistaken for one another.
 */
export function HistoryChart({ points, type, capacityMw }) {
  if (points.length === 0) {
    return <p className="text-sm text-slate-500">No history yet for this plant.</p>
  }

  const innerW = WIDTH - PAD.left - PAD.right
  const innerH = HEIGHT - PAD.top - PAD.bottom
  const times = points.map((p) => new Date(p.at).getTime())
  const minT = times[0]
  const spanT = Math.max(1, times[times.length - 1] - minT)
  const maxY = Math.max(capacityMw, ...points.map((p) => p.maxOutputMw), 1)

  const x = (t) => PAD.left + ((t - minT) / spanT) * innerW
  const y = (v) => PAD.top + innerH - (v / maxY) * innerH

  const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${x(times[i])} ${y(p.outputMw)}`).join(' ')
  const area = `${line} L ${x(times[times.length - 1])} ${y(0)} L ${x(times[0])} ${y(0)} Z`

  return (
    <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="w-full" preserveAspectRatio="none">
      <line
        x1={PAD.left}
        x2={WIDTH - PAD.right}
        y1={y(capacityMw)}
        y2={y(capacityMw)}
        strokeDasharray="4 3"
        className="stroke-slate-300 dark:stroke-slate-700"
      />
      <path d={area} className={FILL[type]} />
      <path d={line} fill="none" strokeWidth="1.5" className={STROKE[type]} />
      {points.map(
        (p, i) =>
          p.resolution === 'ROLLUP' && (
            <circle key={p.at} cx={x(times[i])} cy={y(p.outputMw)} r="1.6" className={DOT[type]} />
          ),
      )}
      <text x={PAD.left} y={HEIGHT - 4} fontSize="9" className="fill-slate-500">
        {time(points[0].at)}
      </text>
      <text x={WIDTH - PAD.right} y={HEIGHT - 4} fontSize="9" textAnchor="end" className="fill-slate-500">
        {time(points[points.length - 1].at)}
      </text>
      <text x={PAD.left} y={y(capacityMw) - 3} fontSize="9" className="fill-slate-500">
        capacity — {mw(capacityMw)}
      </text>
    </svg>
  )
}
