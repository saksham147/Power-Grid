import { useState } from 'react'
import { isUnreachable } from '../lib/api'
import { useSetDeviation, useStatus } from '../lib/queries'
import { Banner, Button, Card, Field } from '../components/ui'
import { mw, mwh } from '../lib/format'

const SUNRISE = 72 / 288
const SUNSET = 216 / 288

/** Where in the simulated day we are, 0 at midnight and 1 at the next. */
function dayFraction(tick) {
  return (((tick % 288) + 288) % 288) / 288
}

function Clock({ status }) {
  const tick = status?.tickNumber ?? 0
  const f = dayFraction(tick)
  const daylight = f >= SUNRISE && f < SUNSET

  return (
    <div>
      <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
        <span className="font-mono text-5xl font-semibold tabular-nums tracking-tight">
          {status?.simulatedTime ?? '--:--'}
        </span>
        <span className="text-sm text-slate-500">
          day {status?.simulatedDay ?? 0} · tick {tick}
        </span>
        <span className="ml-auto text-xs text-slate-500">
          {daylight ? 'daylight' : 'night'} · 1 real second = 1 simulated minute
        </span>
      </div>

      {/* The day bar is not decoration: solar output is zero outside 06:00-18:00,
          so this is what explains the solar column in the fleet table. */}
      <div className="relative mt-4 h-2 w-full overflow-hidden rounded-full bg-slate-800/90 dark:bg-slate-800">
        <div
          className="absolute inset-y-0 bg-amber-300/70 dark:bg-amber-400/40"
          style={{ left: `${SUNRISE * 100}%`, right: `${(1 - SUNSET) * 100}%` }}
        />
        <div
          className="absolute inset-y-0 w-0.5 bg-white mix-blend-difference"
          style={{ left: `calc(${f * 100}% - 1px)` }}
        />
      </div>
      <div className="mt-1 flex justify-between font-mono text-[11px] text-slate-500">
        <span>00:00</span>
        <span>06:00</span>
        <span>12:00</span>
        <span>18:00</span>
        <span>24:00</span>
      </div>
    </div>
  )
}

export default function Simulation() {
  const { data: status, error } = useStatus()
  const setDeviation = useSetDeviation()

  // null means "not edited": the field shows the live value until someone types,
  // derived during render rather than copied into state by an effect.
  const [edit, setEdit] = useState(null)
  const draft = edit ?? String(status?.frequencyDeviation ?? 0)
  const pending = Number(draft) !== status?.frequencyDeviation

  return (
    <div className="space-y-5">
      {error && !isUnreachable(error) && <Banner error={error} />}

      <Card title="Simulated clock">
        <Clock status={status} />
      </Card>

      <div className="grid gap-5 md:grid-cols-2">
        <Card title="Generating now">
          <div className="font-mono text-4xl font-semibold tabular-nums">
            {status ? mw(status.fleetOutputMw) : '—'}
          </div>
          <p className="mt-2 text-xs text-slate-500">
            Power — an instantaneous rate, from the {status?.lastEventCount ?? 0} plant
            {status?.lastEventCount === 1 ? '' : 's'} that reported on the last tick.
          </p>
        </Card>

        <Card title="Energy generated">
          <div className="font-mono text-4xl font-semibold tabular-nums">
            {status ? mwh(status.fleetEnergyMwh) : '—'}
          </div>
          <p className="mt-2 text-xs text-slate-500">
            Power accumulated over simulated time, since the service started. Each tick adds its
            output held for {status?.tickIntervalSeconds ?? 5} simulated minutes.
          </p>
        </Card>
      </div>

      {setDeviation.error && <Banner error={setDeviation.error} />}

      <Card title="Grid frequency">
        <div className="grid gap-3 sm:grid-cols-2">
          <Field
            label="Deviation from 50 Hz"
            type="number"
            step="0.05"
            min="-0.25"
            max="0.25"
            value={draft}
            onChange={(e) => setEdit(e.target.value)}
            hint={
              Number(draft) < 0
                ? 'Grid running slow — thermal opens up, output rises.'
                : Number(draft) > 0
                  ? 'Grid running fast — thermal backs off, output falls.'
                  : 'Nominal — thermal holds its setpoint.'
            }
          />
          <div className="flex items-end gap-2">
            <Button
              variant="primary"
              disabled={!pending || setDeviation.isPending}
              onClick={() => setDeviation.mutate(Number(draft), { onSuccess: () => setEdit(null) })}
            >
              {setDeviation.isPending ? 'Applying…' : 'Apply'}
            </Button>
            <span className="pb-1.5 text-xs text-slate-500">
              Live: {status?.frequencyDeviation ?? 0} Hz
            </span>
          </div>
        </div>
        <p className="mt-3 text-xs text-slate-500">
          Only thermal plants respond. Solar and wind are non-dispatchable — they read the clock,
          not the grid.
        </p>
      </Card>
    </div>
  )
}
