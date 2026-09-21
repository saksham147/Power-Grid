import { useEffect, useState } from 'react'

import { usePlantHistory, usePlantForecast } from '../lib/queries'
import { useUpgradeZone, useDeleteZone } from '../lib/customerQueries'
import { mw, mwh, kw, hz, rupees } from '../lib/format'
import {
  ACCENT, OFFLINE, POSITIVE, NEGATIVE, INK, PLANT_TYPES, STORAGE_TYPES, PROFILE_TYPES, plantCost,
} from './constants'
import { BuildingIcon, SourceIcon, GridIcon, ZoneIcon } from './icons'
import { ErrorBox } from './modals'

// The bottom dashboard. Shows the Grid overview until something on the map is clicked, then that
// thing's details -- a plant, a storage unit, a zone or a single house -- with its actions. It is
// looked up live by id on every render, so the numbers keep ticking while it is open, and a thing
// that gets deleted simply falls back to the Grid view (no stale panel, no effect needed).

const AMBER = '#f59e0b'

const btn = 'rounded-lg border px-3 py-1.5 text-xs font-medium transition disabled:opacity-40'
const btnPrimary = `${btn} border-slate-900 bg-slate-900 text-white hover:bg-slate-700`
const btnPlain = `${btn} border-slate-300 text-slate-600 hover:bg-slate-50`
const btnDanger = `${btn} border-red-200 text-red-600 hover:bg-red-50`

function Stat({ label, value, tone }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
      <p className="text-[10px] text-slate-400">{label}</p>
      <p
        className="mt-0.5 truncate text-sm font-semibold tabular-nums"
        style={{ color: tone === 'positive' ? POSITIVE : tone === 'negative' ? NEGATIVE : INK }}
      >
        {value}
      </p>
    </div>
  )
}

function StatGrid({ children }) {
  return <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-4">{children}</div>
}

function Card({ title, children }) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-3">
      <h3 className="text-[10px] font-medium text-slate-500">{title}</h3>
      <div className="mt-2">{children}</div>
    </section>
  )
}

function BigMeter({ label, pct, color, right }) {
  return (
    <div>
      <div className="flex items-center justify-between text-[11px]">
        <span className="text-slate-500">{label}</span>
        <span className="tabular-nums font-medium text-slate-900">{right}</span>
      </div>
      <div className="mt-1 h-2 w-full overflow-hidden rounded-full bg-slate-100">
        <div className="h-full rounded-full" style={{ width: `${Math.max(0, Math.min(100, pct))}%`, background: color }} />
      </div>
    </div>
  )
}

/** One data series as a filled line -- plain SVG, not worth a charting dependency. `max` fixes the
 *  vertical scale (a plant's rating, say) so a flat 30% output doesn't get stretched to look full. */
function Sparkline({ values, max, color }) {
  const width = 300
  const height = 64
  if (!values || values.length < 2) {
    return <p className="flex h-16 items-center justify-center text-xs text-slate-400">Not enough data yet.</p>
  }
  const top = Math.max(1e-9, max ?? Math.max(...values))
  const points = values.map((v, i) => {
    const x = (i / (values.length - 1)) * width
    const y = height - (Math.min(v, top) / top) * (height - 4) - 2
    return `${x.toFixed(1)},${y.toFixed(1)}`
  })
  return (
    <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" className="h-16 w-full">
      <polygon points={`0,${height} ${points.join(' ')} ${width},${height}`} fill={color} fillOpacity="0.12" />
      <polyline points={points.join(' ')} fill="none" stroke={color} strokeWidth="2" vectorEffect="non-scaling-stroke" />
    </svg>
  )
}

function PanelShell({ icon, title, subtitle, onBack, actions, children }) {
  return (
    <div className="flex flex-col gap-3 p-3 sm:p-4">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border border-slate-200 bg-slate-50">
          {icon}
        </div>
        <div className="min-w-0 flex-1">
          <h2 className="truncate text-sm font-semibold text-slate-900">{title}</h2>
          <p className="truncate text-xs text-slate-400">{subtitle}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {actions}
          {onBack && <button type="button" onClick={onBack} className={btnPlain}>← Grid</button>}
        </div>
      </div>
      {children}
    </div>
  )
}

// ---- Grid overview ----------------------------------------------------------------------------

/** Fixed, in order -- each goal's `current` reads from data the dashboard already polls, nothing
 *  new to fetch. A goal past the first incomplete one is locked (greyed) rather than independently
 *  trackable, so there is always exactly one thing to aim at next. */
const GOALS = [
  { id: 'first-plant', label: 'Build your first plant', target: 1, unit: '', current: (d) => d.plantCount },
  { id: 'five-plants', label: 'Grow the fleet to 5 plants', target: 5, unit: '', current: (d) => d.plantCount },
  { id: 'renewable-40', label: 'Reach 40% renewable output', target: 40, unit: '%', current: (d) => Math.round(d.renewableSharePct) },
  { id: 'ten-customers', label: 'Serve 10 customers', target: 10, unit: '', current: (d) => d.customerCount },
]

const GOALS_STORAGE_KEY = 'power-grid.furthest-goal-index'

function loadFurthestGoalIndex() {
  try {
    return Number(window.localStorage.getItem(GOALS_STORAGE_KEY)) || 0
  } catch {
    return 0
  }
}

function saveFurthestGoalIndex(index) {
  try {
    window.localStorage.setItem(GOALS_STORAGE_KEY, String(index))
  } catch {
    // Private-browsing/blocked storage: the goals still work, they just won't be remembered.
  }
}

function GoalsCard({ data }) {
  const firstIncompleteIndex = GOALS.findIndex((g) => g.current(data) < g.target)
  const activeIndex = firstIncompleteIndex === -1 ? GOALS.length : firstIncompleteIndex

  // Remembers the furthest goal ever reached in this browser -- a write to an external system, so
  // an effect is the right tool, and there's no state to keep in step with it.
  useEffect(() => {
    if (activeIndex > loadFurthestGoalIndex()) saveFurthestGoalIndex(activeIndex)
  }, [activeIndex])

  return (
    <Card title="Goals">
      <div className="space-y-2">
        {GOALS.map((goal, i) => {
          const value = goal.current(data)
          const done = value >= goal.target
          const pct = Math.min(100, (value / goal.target) * 100)
          return (
            <div key={goal.id} className={i > activeIndex ? 'opacity-35' : ''}>
              <div className="flex items-center justify-between text-[11px]">
                <span className={done ? 'text-slate-900' : 'text-slate-500'}>{done ? '✓ ' : ''}{goal.label}</span>
                <span className="tabular-nums text-slate-400">
                  {Math.min(value, goal.target)}{goal.unit}/{goal.target}{goal.unit}
                </span>
              </div>
              <div className="mt-1 h-1 w-full overflow-hidden rounded-full bg-slate-100">
                <div className="h-full rounded-full" style={{ width: `${pct}%`, background: done ? POSITIVE : ACCENT }} />
              </div>
            </div>
          )
        })}
      </div>
    </Card>
  )
}

function MixCard({ plants }) {
  const totals = Object.keys(PLANT_TYPES)
    .map((type) => ({
      type,
      mw: (plants ?? []).filter((p) => p.type === type).reduce((sum, p) => sum + Math.max(0, p.currentOutputMw), 0),
    }))
    .filter((t) => t.mw > 0)
  const totalMw = totals.reduce((sum, t) => sum + t.mw, 0)

  return (
    <Card title="Production mix">
      {totalMw <= 0 ? (
        <p className="text-xs text-slate-400">No output yet.</p>
      ) : (
        <>
          <div className="flex h-2.5 w-full overflow-hidden rounded-full bg-slate-100">
            {totals.map((t) => (
              <div key={t.type} style={{ width: `${(t.mw / totalMw) * 100}%`, background: PLANT_TYPES[t.type].color }} />
            ))}
          </div>
          <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1">
            {totals.map((t) => (
              <span key={t.type} className="flex items-center gap-1 text-[11px] text-slate-500">
                <span className="inline-block h-1.5 w-1.5 rounded-full" style={{ background: PLANT_TYPES[t.type].color }} />
                {PLANT_TYPES[t.type].label} {mw(t.mw)}
              </span>
            ))}
          </div>
        </>
      )}
    </Card>
  )
}

function ScoreboardCard({ reliabilityPct, renewableSharePct, goalsCompletedPct, costEfficiencyLabel }) {
  const rows = [
    ['Reliability', `${Math.round(reliabilityPct)}%`],
    ['Renewable share', `${Math.round(renewableSharePct)}%`],
    ['Goals complete', `${Math.round(goalsCompletedPct)}%`],
    ['Cost efficiency', costEfficiencyLabel],
  ]
  return (
    <Card title="Scoreboard">
      <div className="space-y-1">
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-center justify-between text-[11px]">
            <span className="text-slate-500">{label}</span>
            <span className="tabular-nums font-medium text-slate-900">{value}</span>
          </div>
        ))}
      </div>
    </Card>
  )
}

function ServicesCard({ services }) {
  return (
    <Card title="Services">
      <div className="flex flex-wrap gap-x-4 gap-y-1.5">
        {services.map((s) => (
          <span key={s.label} className="flex items-center gap-1.5 text-[11px] text-slate-500">
            <span className="inline-block h-1.5 w-1.5 rounded-full" style={{ background: s.online ? ACCENT : OFFLINE }} />
            {s.label}
          </span>
        ))}
      </div>
    </Card>
  )
}

function GridView({ o }) {
  const { grid, gridOnline } = o
  const freq = grid?.frequencyDeviation ?? 0
  const goalsData = { plantCount: o.plants?.length ?? 0, renewableSharePct: o.renewableSharePct, customerCount: o.unitCount }
  const goalsCompletedPct = (GOALS.filter((g) => g.current(goalsData) >= g.target).length / GOALS.length) * 100
  const costEfficiencyLabel = o.summary && o.summary.spendRupees > 0
    ? `${(o.summary.revenueRupees / o.summary.spendRupees).toFixed(1)}x`
    : o.summary && o.summary.revenueRupees > 0 ? '∞' : '—'

  const dash = (v) => (gridOnline ? v : '—')

  return (
    <PanelShell
      icon={<GridIcon color={!gridOnline ? OFFLINE : grid?.loadExceeded ? NEGATIVE : ACCENT} size={32} />}
      title="Grid"
      subtitle={gridOnline ? `${grid.simulatedTime} · day ${grid.simulatedDay} · tick ${grid.tickNumber}` : 'Offline'}
      actions={<button type="button" onClick={o.onManageCapacities} className={btnPlain}>Zone capacities</button>}
    >
      <div className="grid gap-3 lg:grid-cols-[1.5fr_1fr_1fr]">
        <div className="space-y-3">
          <StatGrid>
            <Stat
              label="Frequency Δ"
              value={dash(hz(freq))}
              tone={freq < -0.01 ? 'negative' : freq > 0.01 ? 'positive' : undefined}
            />
            <Stat label="Supply" value={dash(kw(grid?.totalSupplyKw ?? 0))} />
            <Stat label="Demand" value={dash(kw(grid?.totalDemandKw ?? 0))} />
            <Stat
              label="Balance"
              value={dash(kw(o.gridBalanceKw))}
              tone={o.gridBalanceKw < 0 ? 'negative' : o.gridBalanceKw > 0 ? 'positive' : undefined}
            />
            <Stat
              label="Status"
              value={dash(grid?.loadExceeded ? 'Overloaded' : 'OK')}
              tone={gridOnline ? (grid.loadExceeded ? 'negative' : 'positive') : undefined}
            />
            <Stat label="Control" value={dash(grid?.autoControlEnabled ? 'Automatic' : 'Manual')} />
            <Stat label="Wallet" value={o.billingOnline ? rupees(o.totalBalanceRupees) : '—'} />
            <Stat label="Revenue" value={o.summary ? rupees(o.summary.revenueRupees) : '—'} />
            <Stat label="Spend" value={o.summary ? rupees(o.summary.spendRupees) : '—'} />
          </StatGrid>

          {o.unlocks?.nextUnlock && (
            <p className="text-[11px] text-slate-400">
              {PLANT_TYPES[o.unlocks.nextUnlock.type]?.label ?? o.unlocks.nextUnlock.type} plants unlock in{' '}
              {Math.max(0, Math.round(o.unlocks.nextUnlock.kwhRemaining)).toLocaleString('en-IN')} more kWh sold.
            </p>
          )}
          <p className="text-[11px] text-slate-400">Click a plant, storage unit, zone or building on the map for its details.</p>
        </div>

        <div className="space-y-3">
          <MixCard plants={o.plants} />
          <ServicesCard services={o.services} />
        </div>

        <div className="space-y-3">
          <GoalsCard data={goalsData} />
          <ScoreboardCard
            reliabilityPct={o.reliabilityPct}
            renewableSharePct={o.renewableSharePct}
            goalsCompletedPct={goalsCompletedPct}
            costEfficiencyLabel={costEfficiencyLabel}
          />
        </div>
      </div>
    </PanelShell>
  )
}

// ---- Plant ------------------------------------------------------------------------------------

function PlantView({ plant, onBack, onEdit, onDelete }) {
  const meta = PLANT_TYPES[plant.type] ?? { color: OFFLINE, label: plant.type }
  const isWeather = plant.type !== 'THERMAL'
  const { data: history } = usePlantHistory(plant.id, true)
  const { data: forecast } = usePlantForecast(plant.id, isWeather)
  const utilization = plant.capacityMw > 0 ? (plant.currentOutputMw / plant.capacityMw) * 100 : 0

  return (
    <PanelShell
      icon={<SourceIcon kind="plant" type={plant.type} size={34} spinning={plant.active && plant.currentOutputMw > 0} />}
      title={plant.name}
      subtitle={`${meta.label} plant · ${plant.active ? 'active' : 'inactive'}`}
      onBack={onBack}
      actions={
        <>
          <button type="button" onClick={() => onEdit(plant)} className={btnPrimary}>Edit</button>
          <button type="button" onClick={() => onDelete(plant)} className={btnDanger}>Decommission</button>
        </>
      }
    >
      <div className="grid gap-3 lg:grid-cols-[1.4fr_1fr_1fr]">
        <div className="space-y-3">
          <BigMeter
            label="Output"
            pct={utilization}
            color={plant.active ? meta.color : OFFLINE}
            right={`${mw(plant.currentOutputMw)} / ${mw(plant.capacityMw)}`}
          />
          <StatGrid>
            <Stat label="Utilization" value={`${Math.round(utilization)}%`} />
            <Stat label="Energy generated" value={mwh(plant.energyMwh)} />
            <Stat label="Min output" value={mw(plant.minOutputMw)} />
            <Stat label="Base output" value={mw(plant.baseOutputMw)} />
            <Stat label="Value" value={rupees(plantCost(plant.type, plant.capacityMw))} />
            <Stat label="Status" value={plant.active ? 'Active' : 'Inactive'} tone={plant.active ? 'positive' : undefined} />
          </StatGrid>
        </div>

        <Card title="Recent output">
          <Sparkline values={(history ?? []).slice().reverse().map((p) => p.outputMw)} max={plant.capacityMw} color={meta.color} />
        </Card>

        {isWeather ? (
          <Card title="Forecast · next 12 ticks">
            <Sparkline values={(forecast ?? []).map((p) => p.outputMw)} max={plant.capacityMw} color={meta.color} />
          </Card>
        ) : (
          <Card title="Forecast">
            <p className="text-xs text-slate-400">Thermal output follows grid demand, not weather, so it isn't forecast.</p>
          </Card>
        )}
      </div>
    </PanelShell>
  )
}

// ---- Storage ----------------------------------------------------------------------------------

function StorageView({ unit, onBack, onEdit, onDelete }) {
  const meta = STORAGE_TYPES[unit.kind] ?? { color: OFFLINE, label: unit.kind }
  const pct = unit.capacityKwh > 0 ? (unit.stateOfChargeKwh / unit.capacityKwh) * 100 : 0

  return (
    <PanelShell
      icon={<SourceIcon kind="storage" type={unit.kind} size={34} />}
      title={unit.name}
      subtitle={`${meta.label} storage · ${unit.active ? 'active' : 'inactive'}`}
      onBack={onBack}
      actions={
        <>
          <button type="button" onClick={() => onEdit(unit)} className={btnPrimary}>Edit</button>
          <button type="button" onClick={() => onDelete(unit)} className={btnDanger}>Delete</button>
        </>
      }
    >
      <div className="space-y-3">
        <BigMeter
          label="State of charge"
          pct={pct}
          color={unit.active ? meta.color : OFFLINE}
          right={`${Math.round(pct)}% · ${Math.round(unit.stateOfChargeKwh).toLocaleString('en-IN')} / ${Math.round(unit.capacityKwh).toLocaleString('en-IN')} kWh`}
        />
        <StatGrid>
          <Stat label="Capacity" value={`${Math.round(unit.capacityKwh).toLocaleString('en-IN')} kWh`} />
          <Stat label="Max charge rate" value={kw(unit.maxChargeRateKw)} />
          <Stat label="Max discharge rate" value={kw(unit.maxDischargeRateKw)} />
          <Stat label="Status" value={unit.active ? 'Active' : 'Inactive'} tone={unit.active ? 'positive' : undefined} />
        </StatGrid>
        <p className="text-[11px] text-slate-400">
          Charges automatically when the grid has a surplus and discharges when it runs short.
        </p>
      </div>
    </PanelShell>
  )
}

// ---- Zone -------------------------------------------------------------------------------------

function ZoneView({ zone, units, demand, capacity, onBack, onSelect, onAddUnit, onManageCapacities }) {
  const [renaming, setRenaming] = useState(false)
  const upgradeZone = useUpgradeZone()
  const deleteZone = useDeleteZone()

  function handleRename(e) {
    e.preventDefault()
    const name = new FormData(e.currentTarget).get('name')
    upgradeZone.mutate({ zoneId: zone.zoneId, name }, { onSuccess: () => setRenaming(false) })
  }

  function handleDelete() {
    if (window.confirm(`Delete zone ${zone.name} and all ${units.length} of its buildings? This cannot be undone.`)) {
      deleteZone.mutate(zone.zoneId)
    }
  }

  const demandKw = demand?.demandKw ?? 0
  const over = Boolean(capacity?.overCapacity)
  const loadPct = capacity?.capacityKw > 0 ? (demandKw / capacity.capacityKw) * 100 : 0

  return (
    <PanelShell
      icon={<ZoneIcon size={30} />}
      title={zone.name}
      subtitle={`${zone.zoneId} · ${units.length} building${units.length === 1 ? '' : 's'}`}
      onBack={onBack}
      actions={
        <>
          <button type="button" onClick={() => onAddUnit(zone.zoneId)} className={btnPrimary}>+ Building</button>
          <button type="button" onClick={onManageCapacities} className={btnPlain}>Capacity</button>
          <button type="button" onClick={() => setRenaming((r) => !r)} className={btnPlain}>Rename</button>
          <button type="button" onClick={handleDelete} className={btnDanger}>Delete zone</button>
        </>
      }
    >
      {renaming && (
        <form onSubmit={handleRename} className="flex items-center gap-2">
          <input
            name="name"
            defaultValue={zone.name}
            required
            autoFocus
            className="w-56 rounded-lg border border-slate-300 px-2.5 py-1.5 text-sm text-slate-900 focus:outline-2 focus:outline-sky-500"
          />
          <button type="submit" className={btnPrimary}>Save</button>
          <button type="button" onClick={() => setRenaming(false)} className={btnPlain}>Cancel</button>
        </form>
      )}
      <ErrorBox error={upgradeZone.error ?? deleteZone.error} />

      <div className="grid gap-3 lg:grid-cols-[1.4fr_1fr]">
        <div className="space-y-3">
          {capacity && (
            <BigMeter
              label="Load against capacity"
              pct={loadPct}
              color={over ? NEGATIVE : loadPct >= 85 ? AMBER : ACCENT}
              right={`${kw(demandKw)} / ${kw(capacity.capacityKw)}${over ? ' · OVER' : ''}`}
            />
          )}
          <StatGrid>
            <Stat label="Demand now" value={kw(demandKw)} />
            <Stat label="Day peak" value={kw(demand?.dayPeakKw ?? 0)} />
            <Stat label="Night peak" value={kw(demand?.nightPeakKw ?? 0)} />
            <Stat
              label="Capacity"
              value={capacity ? kw(capacity.capacityKw) : 'Unmetered'}
              tone={over ? 'negative' : undefined}
            />
          </StatGrid>
        </div>

        <Card title="Buildings">
          {units.length === 0 ? (
            <p className="text-xs text-slate-400">Empty zone — add a house, shop or factory.</p>
          ) : (
            <div className="flex max-h-28 flex-wrap gap-1.5 overflow-y-auto">
              {units.map((u) => (
                <button
                  key={u.unitId}
                  type="button"
                  onClick={() => onSelect('unit', u.unitId)}
                  className="flex items-center gap-1.5 rounded-lg border border-slate-200 px-2 py-1 text-[11px] text-slate-700 hover:bg-slate-50"
                >
                  <BuildingIcon type={u.type} size={18} />
                  <span className="max-w-24 truncate">{u.name}</span>
                  <span className="tabular-nums text-slate-400">{kw(u.demandKw)}</span>
                </button>
              ))}
            </div>
          )}
        </Card>
      </div>
    </PanelShell>
  )
}

// ---- Building ---------------------------------------------------------------------------------

function UnitView({ unit, zone, onBack, onSelect, onEdit, onDelete }) {
  const meta = PROFILE_TYPES[unit.type] ?? { color: OFFLINE, label: unit.type }
  const pct = unit.capacityKw > 0 ? (unit.demandKw / unit.capacityKw) * 100 : 0

  return (
    <PanelShell
      icon={<BuildingIcon type={unit.type} size={34} />}
      title={unit.name}
      subtitle={`${meta.label} · ${unit.unitId}`}
      onBack={onBack}
      actions={
        <>
          <button type="button" onClick={() => onEdit(unit)} className={btnPrimary}>Edit</button>
          <button type="button" onClick={() => onDelete(unit)} className={btnDanger}>Delete</button>
        </>
      }
    >
      <div className="space-y-3">
        <BigMeter
          label="Load"
          pct={pct}
          color={pct >= 100 ? NEGATIVE : pct >= 85 ? AMBER : meta.color}
          right={`${kw(unit.demandKw)} / ${kw(unit.capacityKw)}`}
        />
        <StatGrid>
          <Stat label="Demand now" value={kw(unit.demandKw)} />
          <Stat label="Rated capacity" value={kw(unit.capacityKw)} />
          <Stat label="Load" value={`${Math.round(pct)}%`} tone={pct >= 100 ? 'negative' : undefined} />
          <div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
            <p className="text-[10px] text-slate-400">Zone</p>
            <button
              type="button"
              onClick={() => onSelect('zone', unit.zoneId)}
              className="mt-0.5 max-w-full truncate text-sm font-semibold text-sky-600 hover:text-sky-700"
            >
              {zone?.name ?? unit.zoneId}
            </button>
          </div>
        </StatGrid>
      </div>
    </PanelShell>
  )
}

// ---- The panel --------------------------------------------------------------------------------

/**
 * `data` holds the live lists (plants, storageUnits, zones, units, plus per-zone lookups) and
 * `overview` everything the Grid view needs; `actions` are the Dashboard's own callbacks (it owns
 * the modals and the delete flows). Nothing here fetches except the two per-plant charts, and those
 * only exist while a plant is selected.
 */
export default function DetailPanel({ selection, data, overview, actions }) {
  const back = () => actions.onSelect('grid')

  let view = null
  if (selection.kind === 'plant') {
    const plant = data.plants?.find((p) => p.id === selection.id)
    if (plant) {
      view = <PlantView key={plant.id} plant={plant} onBack={back} onEdit={actions.onEditPlant} onDelete={actions.onDeletePlant} />
    }
  } else if (selection.kind === 'storage') {
    const unit = data.storageUnits?.find((s) => s.id === selection.id)
    if (unit) {
      view = <StorageView key={unit.id} unit={unit} onBack={back} onEdit={actions.onEditStorage} onDelete={actions.onDeleteStorage} />
    }
  } else if (selection.kind === 'zone') {
    const zone = data.zones?.find((z) => z.zoneId === selection.id)
    if (zone) {
      view = (
        <ZoneView
          key={zone.zoneId}
          zone={zone}
          units={data.unitsByZone.get(zone.zoneId) ?? []}
          demand={data.demandByZone.get(zone.zoneId)}
          capacity={data.capacityByZone.get(zone.zoneId)}
          onBack={back}
          onSelect={actions.onSelect}
          onAddUnit={actions.onAddUnit}
          onManageCapacities={actions.onManageCapacities}
        />
      )
    }
  } else if (selection.kind === 'unit') {
    const unit = data.units?.find((u) => u.unitId === selection.id)
    if (unit) {
      view = (
        <UnitView
          key={unit.unitId}
          unit={unit}
          zone={data.zones?.find((z) => z.zoneId === unit.zoneId)}
          onBack={back}
          onSelect={actions.onSelect}
          onEdit={actions.onEditUnit}
          onDelete={actions.onDeleteUnit}
        />
      )
    }
  }

  return (
    <aside className="h-[42vh] max-h-[400px] min-h-[240px] shrink-0 overflow-y-auto border-t border-slate-200 bg-slate-50">
      {view ?? <GridView o={{ ...overview, onManageCapacities: actions.onManageCapacities }} />}
    </aside>
  )
}
