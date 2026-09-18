import { useEffect, useState } from 'react'
import { ReactFlow, Background, Controls, Handle, MarkerType, Position } from '@xyflow/react'
import '@xyflow/react/dist/style.css'

import {
  useStatus, usePlants, useCreatePlant, useUpgradePlant, useDeletePlant, usePlantHistory, usePlantForecast,
} from './lib/queries'
import { useGridStatus } from './lib/gridQueries'
import {
  useDistributionStatus, useZoneCapacities, useCreateZoneCapacity, useUpdateZoneCapacity, useDeleteZoneCapacity,
} from './lib/distributorQueries'
import {
  useDemand, useZones, useCreateZone, useUpgradeZone, useDeleteZone,
  useUnits, useCreateUnit, useUpgradeUnit, useDeleteUnit,
} from './lib/customerQueries'
import {
  useWallets, usePurchasePlant, useUpgradePlantCost, useDecommissionPlant, useUnlocks, usePurchaseStorage,
  useBillingSummary,
} from './lib/billingQueries'
import { useStorageUnits, useCreateStorage, useUpgradeStorage, useDeleteStorage } from './lib/storageQueries'
import { mw, kw, hz, rupees, time } from './lib/format'

// The one accent this page allows itself for service state: white and grayscale carry the page,
// this marks whatever is actually live. Plant and zone nodes are the deliberate exception --
// their color is functional too, it identifies generation type or demand profile, not mood.
const ACCENT = '#0ea5e9'
const OFFLINE = '#cbd5e1'
const POSITIVE = '#059669'
const NEGATIVE = '#e11d48'
const INK = '#0f172a'

const PLANT_TYPES = {
  THERMAL: { color: '#f97316', label: 'Thermal' },
  SOLAR: { color: '#eab308', label: 'Solar' },
  WIND: { color: '#06b6d4', label: 'Wind' },
}

/** What a plant costs to build (or, at the margin, to grow), debited from the shared Grid wallet
 *  before Producer ever sees the request -- see AddPlantModal/UpgradePlantModal. This is a preview
 *  only: the server (Billing.billing.PlantPricing) computes and charges the authoritative number
 *  from the same formula, so this must mirror it exactly or the preview lies. cost = max(minimum,
 *  base + progressive per-MW cost): base is the fixed setup cost, minimum is a floor so a tiny
 *  plant is never near-free, and the per-MW cost is banded like a tax bracket -- each band only
 *  charges its own rate on the slice of capacity inside it, rising band to band. Bands are
 *  calibrated against this simulation's real plant sizes -- Thermal up to ~900 MW, Wind ~150 MW,
 *  Solar ~200 MW. */
const PLANT_RATES = {
  THERMAL: { base: 2000, minimum: 5000, bands: [{ uptoMw: 300, perMw: 8 }, { uptoMw: 600, perMw: 10 }, { uptoMw: Infinity, perMw: 14 }] },
  WIND: { base: 1000, minimum: 3000, bands: [{ uptoMw: 50, perMw: 16 }, { uptoMw: 100, perMw: 20 }, { uptoMw: Infinity, perMw: 26 }] },
  SOLAR: { base: 500, minimum: 2000, bands: [{ uptoMw: 50, perMw: 8 }, { uptoMw: 150, perMw: 10 }, { uptoMw: Infinity, perMw: 13 }] },
}

function plantCost(type, capacityMw) {
  const { base, minimum, bands } = PLANT_RATES[type]
  const mw = capacityMw || 0

  let bandedCost = 0
  let coveredMw = 0
  for (const band of bands) {
    const mwInBand = Math.max(0, Math.min(mw, band.uptoMw) - coveredMw)
    bandedCost += mwInBand * band.perMw
    coveredMw = band.uptoMw
    if (mw <= band.uptoMw) break
  }

  return Math.max(minimum, base + bandedCost)
}

const STORAGE_TYPES = {
  BATTERY: { color: '#84cc16', label: 'Battery' },
  HYDROGEN: { color: '#38bdf8', label: 'Hydrogen' },
}

/** Mirrors Billing.billing.StoragePricing exactly -- see PLANT_RATES's own comment for why this
 *  has to match the server's formula rather than approximate it. */
const STORAGE_RATES = {
  BATTERY: { base: 1500, perKwh: 5, minimum: 3000 },
  HYDROGEN: { base: 3000, perKwh: 8, minimum: 6000 },
}

function storageCost(kind, capacityKwh) {
  const { base, perKwh, minimum } = STORAGE_RATES[kind]
  return Math.max(minimum, base + perKwh * (capacityKwh || 0))
}

const PROFILE_TYPES = {
  RESIDENTIAL: { color: '#8b5cf6', label: 'Residential' },
  COMMERCIAL: { color: '#ec4899', label: 'Commercial' },
  INDUSTRIAL: { color: '#14b8a6', label: 'Industrial' },
  GOV: { color: '#f59e0b', label: 'Government' },
}

const NARROW_BREAKPOINT = 768
const WIDE_COLUMN_ROW = 100
const NARROW_ROW = 190

/** Below this width the flow stacks top-to-bottom instead of left-to-right -- see Dashboard's
 *  own comment on the ReactFlow key for why that's a layout change, not just a zoom level. */
function useNarrowViewport() {
  const [narrow, setNarrow] = useState(() => window.innerWidth < NARROW_BREAKPOINT)
  useEffect(() => {
    const onResize = () => setNarrow(window.innerWidth < NARROW_BREAKPOINT)
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])
  return narrow
}

function ServiceNode({ data }) {
  const dotColor = data.online ? ACCENT : OFFLINE
  const targetPosition = data.narrow ? Position.Top : Position.Left
  const sourcePosition = data.narrow ? Position.Bottom : Position.Right

  return (
    <div className="w-60 overflow-hidden rounded-2xl border bg-white shadow-sm" style={{ borderColor: '#e2e8f0' }}>
      {data.target && <Handle type="target" position={targetPosition} style={{ background: dotColor, border: 'none' }} />}

      <div className="border-b border-slate-100 px-4 py-3">
        <div className="flex items-center gap-2">
          <span className="inline-block h-2 w-2 shrink-0 rounded-full" style={{ background: dotColor }} />
          <span className="text-sm font-semibold text-slate-900">{data.label}</span>
        </div>
        <p className="mt-0.5 truncate text-[11px] text-slate-400">{data.online ? data.subtitle : 'Offline'}</p>
      </div>

      <div className="space-y-1.5 px-4 py-3">
        {data.stats.map((stat) => (
          <div key={stat.label} className="flex items-center justify-between gap-3 text-xs">
            <span className="text-slate-500">{stat.label}</span>
            <span
              className="tabular-nums font-medium"
              style={{ color: !data.online ? '#cbd5e1' : stat.tone === 'positive' ? POSITIVE : stat.tone === 'negative' ? NEGATIVE : INK }}
            >
              {data.online ? stat.value : '—'}
            </span>
          </div>
        ))}
      </div>

      {data.onManage && (
        <button
          type="button"
          onClick={data.onManage}
          disabled={!data.online}
          className="block w-full border-t border-slate-100 py-2 text-center text-[10px] font-medium text-slate-500 hover:bg-slate-50 hover:text-slate-900 disabled:cursor-default disabled:text-slate-300 disabled:hover:bg-transparent"
        >
          {data.manageLabel}
        </button>
      )}

      {data.source && <Handle type="source" position={sourcePosition} style={{ background: dotColor, border: 'none' }} />}
    </div>
  )
}

function PlantNode({ data }) {
  const meta = PLANT_TYPES[data.plantType] ?? { color: OFFLINE, label: data.plantType }
  const dotColor = data.active ? meta.color : OFFLINE
  const sourcePosition = data.narrow ? Position.Bottom : Position.Right

  return (
    <div
      className="w-44 overflow-hidden rounded-xl border bg-white shadow-sm"
      style={{ borderColor: '#e2e8f0', borderTop: `3px solid ${dotColor}` }}
    >
      <div className="px-3 py-2">
        <div className="flex items-center gap-1.5">
          <span className="inline-block h-1.5 w-1.5 shrink-0 rounded-full" style={{ background: dotColor }} />
          <span className="truncate text-xs font-semibold text-slate-900">{data.name}</span>
        </div>
        <p className="mt-0.5 text-[10px] text-slate-400">{meta.label}{data.active ? '' : ' · inactive'}</p>
        <p className="mt-1 text-[11px] font-medium tabular-nums text-slate-900">
          {mw(data.currentOutputMw)} <span className="font-normal text-slate-400">/ {mw(data.capacityMw)}</span>
        </p>
      </div>

      <div className="flex divide-x divide-slate-100 border-t border-slate-100 text-[10px] font-medium text-slate-500">
        <button type="button" onClick={data.onUpgrade} className="flex-1 py-1.5 hover:bg-slate-50 hover:text-slate-900">
          Edit
        </button>
        <button type="button" onClick={data.onViewHistory} className="flex-1 py-1.5 hover:bg-slate-50 hover:text-slate-900">
          Log
        </button>
        {data.onViewForecast && (
          <button type="button" onClick={data.onViewForecast} className="flex-1 py-1.5 hover:bg-slate-50 hover:text-slate-900">
            Forecast
          </button>
        )}
        <button type="button" onClick={data.onDelete} className="flex-1 py-1.5 hover:bg-red-50 hover:text-red-600">
          Delete
        </button>
      </div>

      <Handle type="source" position={sourcePosition} style={{ background: dotColor, border: 'none' }} />
    </div>
  )
}

/** A battery or hydrogen unit -- mirrors PlantNode's shape, but shows state of charge against
 *  capacity instead of output against rating, and has no Log/Forecast buttons (charge state has
 *  no history endpoint and nothing to forecast -- it's reactive to the grid, not weather-driven). */
function StorageNode({ data }) {
  const meta = STORAGE_TYPES[data.kind] ?? { color: OFFLINE, label: data.kind }
  const dotColor = data.active ? meta.color : OFFLINE
  const sourcePosition = data.narrow ? Position.Bottom : Position.Right
  const chargePct = data.capacityKwh > 0 ? Math.round((data.stateOfChargeKwh / data.capacityKwh) * 100) : 0

  return (
    <div
      className="w-44 overflow-hidden rounded-xl border bg-white shadow-sm"
      style={{ borderColor: '#e2e8f0', borderTop: `3px solid ${dotColor}` }}
    >
      <div className="px-3 py-2">
        <div className="flex items-center gap-1.5">
          <span className="inline-block h-1.5 w-1.5 shrink-0 rounded-full" style={{ background: dotColor }} />
          <span className="truncate text-xs font-semibold text-slate-900">{data.name}</span>
        </div>
        <p className="mt-0.5 text-[10px] text-slate-400">{meta.label}{data.active ? '' : ' · inactive'}</p>
        <p className="mt-1 text-[11px] font-medium tabular-nums text-slate-900">
          {chargePct}% <span className="font-normal text-slate-400">charged</span>
        </p>
      </div>

      <div className="flex divide-x divide-slate-100 border-t border-slate-100 text-[10px] font-medium text-slate-500">
        <button type="button" onClick={data.onUpgrade} className="flex-1 py-1.5 hover:bg-slate-50 hover:text-slate-900">
          Edit
        </button>
        <button type="button" onClick={data.onDelete} className="flex-1 py-1.5 hover:bg-red-50 hover:text-red-600">
          Delete
        </button>
      </div>

      <Handle type="source" position={sourcePosition} style={{ background: dotColor, border: 'none' }} />
    </div>
  )
}

/** Live THERMAL/SOLAR/WIND share of current fleet output, as a stacked horizontal bar -- reuses
 *  usePlants() data already polled for the plant column, and PLANT_TYPES' colors so a segment
 *  always matches the plant cards it's summarizing. Nothing to show (no plants, or all at 0 MW)
 *  renders nothing rather than an empty gray bar. */
function ProductionMixBar({ plants }) {
  const totals = Object.keys(PLANT_TYPES).map((type) => ({
    type,
    mw: (plants ?? []).filter((p) => p.type === type).reduce((sum, p) => sum + Math.max(0, p.currentOutputMw), 0),
  }))
  const totalMw = totals.reduce((sum, t) => sum + t.mw, 0)
  if (totalMw <= 0) return null

  return (
    <div className="pointer-events-none absolute left-4 top-4 z-[5] w-56 rounded-xl border border-slate-200 bg-white/95 p-3 shadow-sm backdrop-blur">
      <p className="text-[10px] font-medium text-slate-500">Production mix</p>
      <div className="mt-2 flex h-2.5 w-full overflow-hidden rounded-full bg-slate-100">
        {totals.filter((t) => t.mw > 0).map((t) => (
          <div
            key={t.type}
            style={{ width: `${(t.mw / totalMw) * 100}%`, background: PLANT_TYPES[t.type].color }}
          />
        ))}
      </div>
      <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1">
        {totals.filter((t) => t.mw > 0).map((t) => (
          <span key={t.type} className="flex items-center gap-1 text-[10px] text-slate-500">
            <span className="inline-block h-1.5 w-1.5 rounded-full" style={{ background: PLANT_TYPES[t.type].color }} />
            {PLANT_TYPES[t.type].label} {mw(t.mw)}
          </span>
        ))}
      </div>
    </div>
  )
}

/** Fixed, in order -- each goal's `current` reads from data the dashboard already polls, nothing
 *  new to fetch. Mirrors the decompiled reference game's StartingGoal -> AdvanceGoal chain: goals
 *  are shown in sequence, and a goal past the first incomplete one is locked (greyed) rather than
 *  independently trackable, so there is always exactly one thing to aim at next. */
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
    // Private-browsing/blocked storage: the panel still works, it just won't remember next visit.
  }
}

/** A small always-current scoreboard of sequential objectives -- no backend, no session concept,
 *  just a live readout derived from data already on screen. */
function GoalsPanel({ plantCount, renewableSharePct, customerCount }) {
  const [furthest, setFurthest] = useState(loadFurthestGoalIndex)
  const data = { plantCount, renewableSharePct, customerCount }

  const firstIncompleteIndex = GOALS.findIndex((g) => g.current(data) < g.target)
  const activeIndex = firstIncompleteIndex === -1 ? GOALS.length : firstIncompleteIndex

  useEffect(() => {
    if (activeIndex > furthest) {
      setFurthest(activeIndex)
      saveFurthestGoalIndex(activeIndex)
    }
  }, [activeIndex, furthest])

  return (
    <div className="pointer-events-none absolute right-4 top-4 z-[5] w-56 rounded-xl border border-slate-200 bg-white/95 p-3 shadow-sm backdrop-blur">
      <p className="text-[10px] font-medium text-slate-500">Goals</p>
      <div className="mt-2 space-y-2">
        {GOALS.map((goal, i) => {
          const value = goal.current(data)
          const done = value >= goal.target
          const locked = i > activeIndex
          const pct = Math.min(100, (value / goal.target) * 100)
          return (
            <div key={goal.id} className={locked ? 'opacity-35' : ''}>
              <div className="flex items-center justify-between text-[10px]">
                <span className={done ? 'text-slate-900' : 'text-slate-500'}>
                  {done ? '✓ ' : ''}{goal.label}
                </span>
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
    </div>
  )
}

/** Four live, independent axes -- no session, no leaderboard, just always-current numbers derived
 *  from data the dashboard already polls (plus one small Billing endpoint for revenue/spend). */
function ScoreboardPanel({ reliabilityPct, renewableSharePct, goalsCompletedPct, costEfficiencyLabel }) {
  const rows = [
    { label: 'Reliability', value: `${Math.round(reliabilityPct)}%` },
    { label: 'Renewable share', value: `${Math.round(renewableSharePct)}%` },
    { label: 'Goals complete', value: `${Math.round(goalsCompletedPct)}%` },
    { label: 'Cost efficiency', value: costEfficiencyLabel },
  ]

  return (
    <div className="pointer-events-none absolute bottom-4 right-4 z-[5] w-56 rounded-xl border border-slate-200 bg-white/95 p-3 shadow-sm backdrop-blur">
      <p className="text-[10px] font-medium text-slate-500">Scoreboard</p>
      <div className="mt-2 space-y-1">
        {rows.map((row) => (
          <div key={row.label} className="flex items-center justify-between text-[10px]">
            <span className="text-slate-500">{row.label}</span>
            <span className="tabular-nums font-medium text-slate-900">{row.value}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

/**
 * A zone is a pure container of units now -- see Customer.domain.Zone -- so it carries no single
 * type of its own to color by (a zone can mix houses, factories and commercial buildings). The
 * whole card is a button: clicking it opens the zone's unit list, where the actual house/factory/
 * commercial CRUD from the user's own sketch lives.
 */
function ZoneNode({ data }) {
  const targetPosition = data.narrow ? Position.Top : Position.Left
  const sourcePosition = data.narrow ? Position.Bottom : Position.Right

  return (
    <button
      type="button"
      onClick={data.onManage}
      className="w-44 overflow-hidden rounded-xl border bg-white text-left shadow-sm transition hover:border-slate-300"
      style={{ borderColor: '#e2e8f0', borderTop: `3px solid ${ACCENT}` }}
    >
      <Handle type="target" position={targetPosition} style={{ background: ACCENT, border: 'none' }} />

      <div className="px-3 py-2">
        <span className="block truncate text-xs font-semibold text-slate-900">{data.name}</span>
        <p className="mt-0.5 text-[10px] text-slate-400">
          {data.unitCount} unit{data.unitCount === 1 ? '' : 's'} · manage
        </p>
        <p className="mt-1 text-[11px] font-medium tabular-nums text-slate-900">{kw(data.demandKw)}</p>
      </div>

      <Handle type="source" position={sourcePosition} style={{ background: ACCENT, border: 'none' }} />
    </button>
  )
}

/** One house, factory, government building or commercial unit -- a zone's own children, drawn as
 *  their own column exactly the way Plant is, rather than hidden inside the zone's own modal. */
function CustomerNode({ data }) {
  const meta = PROFILE_TYPES[data.profileType] ?? { color: OFFLINE, label: data.profileType }
  const targetPosition = data.narrow ? Position.Top : Position.Left
  const sourcePosition = data.narrow ? Position.Bottom : Position.Right

  return (
    <div
      className="w-44 overflow-hidden rounded-xl border bg-white shadow-sm"
      style={{ borderColor: '#e2e8f0', borderTop: `3px solid ${meta.color}` }}
    >
      <Handle type="target" position={targetPosition} style={{ background: meta.color, border: 'none' }} />

      <div className="px-3 py-2">
        <div className="flex items-center gap-1.5">
          <span className="inline-block h-1.5 w-1.5 shrink-0 rounded-full" style={{ background: meta.color }} />
          <span className="truncate text-xs font-semibold text-slate-900">{data.name}</span>
        </div>
        <p className="mt-0.5 text-[10px] text-slate-400">{meta.label} · {data.zoneName}</p>
        <p className="mt-1 text-[11px] font-medium tabular-nums text-slate-900">
          {kw(data.demandKw)} <span className="font-normal text-slate-400">/ {kw(data.capacityKw)}</span>
        </p>
      </div>

      <div className="flex divide-x divide-slate-100 border-t border-slate-100 text-[10px] font-medium text-slate-500">
        <button type="button" onClick={data.onEdit} className="flex-1 py-1.5 hover:bg-slate-50 hover:text-slate-900">
          Edit
        </button>
        <button type="button" onClick={data.onDelete} className="flex-1 py-1.5 hover:bg-red-50 hover:text-red-600">
          Delete
        </button>
      </div>

      <Handle type="source" position={sourcePosition} style={{ background: meta.color, border: 'none' }} />
    </div>
  )
}

function AddNode({ data }) {
  return (
    <button
      type="button"
      onClick={data.onClick}
      className="flex w-44 cursor-pointer flex-col items-center justify-center gap-1 rounded-xl border border-dashed border-slate-300 bg-white px-3 py-4 text-slate-400 transition hover:border-slate-400 hover:text-slate-600"
    >
      <span className="text-lg leading-none">+</span>
      <span className="text-[10px] font-medium">{data.label}</span>
    </button>
  )
}

const nodeTypes = {
  service: ServiceNode, plant: PlantNode, storage: StorageNode, zone: ZoneNode, customer: CustomerNode, add: AddNode,
}

/** One shared edge shape so "live" isn't left to each edge's own defaults. */
function flowEdge(id, source, target, label, live) {
  const color = live ? ACCENT : OFFLINE
  return {
    id,
    source,
    target,
    label,
    animated: live,
    style: { stroke: color, strokeWidth: live ? 2.5 : 1.5, strokeDasharray: live ? undefined : '5 4' },
    markerEnd: { type: MarkerType.ArrowClosed, color, width: 16, height: 16 },
    labelStyle: { fill: '#94a3b8', fontWeight: 600, fontSize: 10 },
    labelBgStyle: { fill: '#ffffff' },
  }
}

/** Shared shell every modal in this file uses: a dimmed backdrop that closes on click, and a
 *  centered white card that doesn't. */
function ModalShell({ onClose, children, wide }) {
  return (
    <div className="fixed inset-0 z-10 flex items-center justify-center bg-slate-900/20 px-4" onClick={onClose}>
      <div
        onClick={(e) => e.stopPropagation()}
        className={`w-full ${wide ? 'max-w-md' : 'max-w-sm'} rounded-2xl border border-slate-200 bg-white p-5 shadow-lg`}
      >
        {children}
      </div>
    </div>
  )
}

function ErrorBox({ error }) {
  if (!error) return null
  return (
    <div className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
      <p>{error.message}</p>
      {error.details?.length > 0 && (
        <ul className="mt-1 list-disc pl-4">
          {error.details.map((d) => <li key={d}>{d}</li>)}
        </ul>
      )}
    </div>
  )
}

const inputClass =
  'mt-1 w-full rounded-lg border border-slate-300 px-2.5 py-1.5 text-sm tabular-nums text-slate-900 focus:outline-2 focus:outline-sky-500'

function AddPlantModal({ onClose }) {
  const [type, setType] = useState('THERMAL')
  const [capacityMw, setCapacityMw] = useState(0)
  const [spendError, setSpendError] = useState(null)
  const purchase = usePurchasePlant()
  const createPlant = useCreatePlant(onClose)
  // Server-side enforced regardless (purchasePlant rejects a locked type with 403) -- this is
  // only so the picker doesn't invite a submit that's going to fail.
  const { data: unlocks } = useUnlocks()
  const unlockedTypes = unlocks?.unlockedTypes ?? Object.keys(PLANT_TYPES)
  // Preview only -- the server recomputes this from the same formula and charges that, never this
  // value, so the estimate shown here can never under- or over-charge. A plant belongs to no zone
  // (Producer's own PowerPlant has no zone reference -- it serves the whole grid), so this is
  // charged against the one shared Grid wallet; there is nothing to pick a payer zone for.
  const cost = plantCost(type, capacityMw)

  async function handleSubmit(e) {
    e.preventDefault()
    setSpendError(null)
    const form = new FormData(e.currentTarget)

    try {
      await purchase.mutateAsync({ plantType: type, capacityMw })
    } catch (err) {
      setSpendError(err)
      return
    }

    createPlant.mutate({
      name: form.get('name'),
      type,
      capacityMw: Number(form.get('capacityMw')),
      minOutputMw: Number(form.get('minOutputMw') || 0),
      baseOutputMw: Number(form.get('baseOutputMw') || 0),
    })
  }

  return (
    <ModalShell onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <h2 className="text-sm font-semibold text-slate-900">Add plant</h2>

        <label className="mt-4 block text-xs text-slate-500">
          Name
          <input name="name" required className={inputClass.replace('tabular-nums', '')} />
        </label>

        <label className="mt-3 block text-xs text-slate-500">
          Type
          <select
            name="type"
            value={type}
            onChange={(e) => setType(e.target.value)}
            className={inputClass.replace('tabular-nums', '')}
          >
            {Object.entries(PLANT_TYPES).map(([value, meta]) => (
              <option key={value} value={value} disabled={!unlockedTypes.includes(value)}>
                {meta.label}{unlockedTypes.includes(value) ? '' : ' (locked)'}
              </option>
            ))}
          </select>
        </label>

        {unlocks?.nextUnlock && (
          <p className="mt-1.5 text-[10px] text-slate-400">
            {PLANT_TYPES[unlocks.nextUnlock.type]?.label ?? unlocks.nextUnlock.type} unlocks in{' '}
            {Math.max(0, Math.round(unlocks.nextUnlock.kwhRemaining)).toLocaleString('en-IN')} more kWh sold.
          </p>
        )}

        <div className="mt-3 grid grid-cols-3 gap-2">
          <label className="block text-xs text-slate-500">
            Capacity MW
            <input
              name="capacityMw"
              type="number"
              step="0.1"
              min="0.1"
              required
              value={capacityMw || ''}
              onChange={(e) => setCapacityMw(Number(e.target.value))}
              className={inputClass}
            />
          </label>
          <label className="block text-xs text-slate-500">
            Min MW
            <input name="minOutputMw" type="number" step="0.1" min="0" defaultValue="0" className={inputClass} />
          </label>
          <label className="block text-xs text-slate-500">
            Base MW
            <input name="baseOutputMw" type="number" step="0.1" min="0" defaultValue="0" className={inputClass} />
          </label>
        </div>

        <div className="mt-4 flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs">
          <span className="text-slate-500">Cost, from the Grid wallet</span>
          <span className="font-semibold tabular-nums text-slate-900">{rupees(cost)}</span>
        </div>

        <ErrorBox error={spendError ?? createPlant.error} />

        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50">
            Cancel
          </button>
          <button
            type="submit"
            disabled={purchase.isPending || createPlant.isPending}
            className="rounded-lg border border-slate-900 bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-40"
          >
            {purchase.isPending ? 'Paying…' : createPlant.isPending ? 'Adding…' : `Pay ${rupees(cost)} & add`}
          </button>
        </div>
      </form>
    </ModalShell>
  )
}

function UpgradePlantModal({ plant, onClose }) {
  const [capacityMw, setCapacityMw] = useState(plant.capacityMw)
  const [spendError, setSpendError] = useState(null)
  const upgradeCost = useUpgradePlantCost()
  const upgradePlant = useUpgradePlant(onClose)
  // Growing a plant costs the difference between its old and new price (see PLANT_RATES); shrinking
  // it, or leaving capacity unchanged, is free -- matches Billing.api.WalletController#upgradePlant
  // exactly, so this preview can never promise a charge the server won't also make.
  const extraCost = Math.max(0, plantCost(plant.type, capacityMw) - plantCost(plant.type, plant.capacityMw))

  async function handleSubmit(e) {
    e.preventDefault()
    setSpendError(null)
    const form = new FormData(e.currentTarget)

    if (extraCost > 0) {
      try {
        await upgradeCost.mutateAsync({
          plantType: plant.type,
          oldCapacityMw: plant.capacityMw,
          newCapacityMw: capacityMw,
        })
      } catch (err) {
        setSpendError(err)
        return
      }
    }

    upgradePlant.mutate({
      id: plant.id,
      name: form.get('name'),
      capacityMw,
      minOutputMw: Number(form.get('minOutputMw') || 0),
      baseOutputMw: Number(form.get('baseOutputMw') || 0),
    })
  }

  return (
    <ModalShell onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <h2 className="text-sm font-semibold text-slate-900">Edit {plant.name}</h2>
        <p className="mt-0.5 text-xs text-slate-400">{PLANT_TYPES[plant.type]?.label ?? plant.type} · type can't change</p>

        <label className="mt-4 block text-xs text-slate-500">
          Name
          <input name="name" required defaultValue={plant.name} className={inputClass.replace('tabular-nums', '')} />
        </label>

        <div className="mt-3 grid grid-cols-3 gap-2">
          <label className="block text-xs text-slate-500">
            Capacity MW
            <input
              name="capacityMw"
              type="number"
              step="0.1"
              min="0.1"
              required
              value={capacityMw}
              onChange={(e) => setCapacityMw(Number(e.target.value))}
              className={inputClass}
            />
          </label>
          <label className="block text-xs text-slate-500">
            Min MW
            <input name="minOutputMw" type="number" step="0.1" min="0" defaultValue={plant.minOutputMw} className={inputClass} />
          </label>
          <label className="block text-xs text-slate-500">
            Base MW
            <input name="baseOutputMw" type="number" step="0.1" min="0" defaultValue={plant.baseOutputMw} className={inputClass} />
          </label>
        </div>

        <div className="mt-4 flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs">
          <span className="text-slate-500">
            {extraCost > 0 ? 'Extra cost to grow, from the Grid wallet' : 'Extra cost to grow'}
          </span>
          <span className="font-semibold tabular-nums text-slate-900">
            {extraCost > 0 ? rupees(extraCost) : 'Free'}
          </span>
        </div>

        <ErrorBox error={spendError ?? upgradePlant.error} />

        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50">
            Cancel
          </button>
          <button
            type="submit"
            disabled={upgradeCost.isPending || upgradePlant.isPending}
            className="rounded-lg border border-slate-900 bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-40"
          >
            {upgradeCost.isPending
              ? 'Paying…'
              : upgradePlant.isPending
                ? 'Saving…'
                : extraCost > 0 ? `Pay ${rupees(extraCost)} & save` : 'Save changes'}
          </button>
        </div>
      </form>
    </ModalShell>
  )
}

/** Mirrors AddPlantModal exactly: pay Billing (storageCost preview, server-authoritative charge),
 *  then create in Producer -- same two-step orchestration, same reasoning (storage serves the
 *  whole grid, not one zone, so there's nothing to pick a payer zone for). */
function AddStorageModal({ onClose }) {
  const [kind, setKind] = useState('BATTERY')
  const [capacityKwh, setCapacityKwh] = useState(0)
  const [spendError, setSpendError] = useState(null)
  const purchase = usePurchaseStorage()
  const createStorage = useCreateStorage(onClose)
  const cost = storageCost(kind, capacityKwh)

  async function handleSubmit(e) {
    e.preventDefault()
    setSpendError(null)
    const form = new FormData(e.currentTarget)

    try {
      await purchase.mutateAsync({ kind, capacityKwh })
    } catch (err) {
      setSpendError(err)
      return
    }

    createStorage.mutate({
      name: form.get('name'),
      kind,
      capacityKwh: Number(form.get('capacityKwh')),
      maxChargeRateKw: Number(form.get('maxChargeRateKw') || 0),
      maxDischargeRateKw: Number(form.get('maxDischargeRateKw') || 0),
    })
  }

  return (
    <ModalShell onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <h2 className="text-sm font-semibold text-slate-900">Add storage</h2>

        <label className="mt-4 block text-xs text-slate-500">
          Name
          <input name="name" required className={inputClass.replace('tabular-nums', '')} />
        </label>

        <label className="mt-3 block text-xs text-slate-500">
          Kind
          <select
            name="kind"
            value={kind}
            onChange={(e) => setKind(e.target.value)}
            className={inputClass.replace('tabular-nums', '')}
          >
            {Object.entries(STORAGE_TYPES).map(([value, meta]) => (
              <option key={value} value={value}>{meta.label}</option>
            ))}
          </select>
        </label>

        <div className="mt-3 grid grid-cols-3 gap-2">
          <label className="block text-xs text-slate-500">
            Capacity kWh
            <input
              name="capacityKwh"
              type="number"
              step="1"
              min="1"
              required
              value={capacityKwh || ''}
              onChange={(e) => setCapacityKwh(Number(e.target.value))}
              className={inputClass}
            />
          </label>
          <label className="block text-xs text-slate-500">
            Max charge kW
            <input name="maxChargeRateKw" type="number" step="1" min="0" defaultValue="0" className={inputClass} />
          </label>
          <label className="block text-xs text-slate-500">
            Max discharge kW
            <input name="maxDischargeRateKw" type="number" step="1" min="0" defaultValue="0" className={inputClass} />
          </label>
        </div>

        <div className="mt-4 flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs">
          <span className="text-slate-500">Cost, from the Grid wallet</span>
          <span className="font-semibold tabular-nums text-slate-900">{rupees(cost)}</span>
        </div>

        <ErrorBox error={spendError ?? createStorage.error} />

        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50">
            Cancel
          </button>
          <button
            type="submit"
            disabled={purchase.isPending || createStorage.isPending}
            className="rounded-lg border border-slate-900 bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-40"
          >
            {purchase.isPending ? 'Paying…' : createStorage.isPending ? 'Adding…' : `Pay ${rupees(cost)} & add`}
          </button>
        </div>
      </form>
    </ModalShell>
  )
}

function UpgradeStorageModal({ unit, onClose }) {
  const upgradeStorage = useUpgradeStorage(onClose)

  function handleSubmit(e) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    upgradeStorage.mutate({
      id: unit.id,
      name: form.get('name'),
      capacityKwh: Number(form.get('capacityKwh')),
      maxChargeRateKw: Number(form.get('maxChargeRateKw') || 0),
      maxDischargeRateKw: Number(form.get('maxDischargeRateKw') || 0),
    })
  }

  return (
    <ModalShell onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <h2 className="text-sm font-semibold text-slate-900">Edit {unit.name}</h2>
        <p className="mt-0.5 text-xs text-slate-400">{STORAGE_TYPES[unit.kind]?.label ?? unit.kind} · kind can't change</p>

        <label className="mt-4 block text-xs text-slate-500">
          Name
          <input name="name" required defaultValue={unit.name} className={inputClass.replace('tabular-nums', '')} />
        </label>

        <div className="mt-3 grid grid-cols-3 gap-2">
          <label className="block text-xs text-slate-500">
            Capacity kWh
            <input name="capacityKwh" type="number" step="1" min="1" required defaultValue={unit.capacityKwh} className={inputClass} />
          </label>
          <label className="block text-xs text-slate-500">
            Max charge kW
            <input name="maxChargeRateKw" type="number" step="1" min="0" defaultValue={unit.maxChargeRateKw} className={inputClass} />
          </label>
          <label className="block text-xs text-slate-500">
            Max discharge kW
            <input name="maxDischargeRateKw" type="number" step="1" min="0" defaultValue={unit.maxDischargeRateKw} className={inputClass} />
          </label>
        </div>

        <ErrorBox error={upgradeStorage.error} />

        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50">
            Cancel
          </button>
          <button
            type="submit"
            disabled={upgradeStorage.isPending}
            className="rounded-lg border border-slate-900 bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-40"
          >
            {upgradeStorage.isPending ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </form>
    </ModalShell>
  )
}

function PlantHistoryModal({ plant, onClose }) {
  const { data: points, isLoading, isError } = usePlantHistory(plant.id, true)

  return (
    <ModalShell onClose={onClose}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-sm font-semibold text-slate-900">{plant.name} — log</h2>
          <p className="mt-0.5 text-xs text-slate-400">most recent readings first</p>
        </div>
        <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-600">✕</button>
      </div>

      <div className="mt-3 max-h-72 overflow-y-auto rounded-lg border border-slate-100">
        {isLoading && <p className="px-3 py-4 text-center text-xs text-slate-400">Loading…</p>}
        {isError && <p className="px-3 py-4 text-center text-xs text-red-600">Couldn't load history.</p>}
        {points?.length === 0 && <p className="px-3 py-4 text-center text-xs text-slate-400">No history yet.</p>}
        {points?.map((point, i) => (
          <div key={`${point.at}-${i}`} className="flex items-center justify-between gap-3 border-b border-slate-50 px-3 py-1.5 text-xs last:border-0">
            <span className="text-slate-500">{time(point.at)}</span>
            <span className="tabular-nums font-medium text-slate-900">{mw(point.outputMw)}</span>
          </div>
        ))}
      </div>
    </ModalShell>
  )
}

/** SOLAR/WIND only -- THERMAL has no forecast endpoint, see Producer.generation.ForecastService.
 *  A plain inline SVG polyline rather than a charting library: one data series, twelve points,
 *  not worth a new dependency for. */
function ForecastModal({ plant, onClose }) {
  const { data: points, isLoading, isError } = usePlantForecast(plant.id, true)
  const meta = PLANT_TYPES[plant.type] ?? { color: OFFLINE, label: plant.type }

  const width = 320
  const height = 120
  const maxMw = Math.max(1, ...(points ?? []).map((p) => p.outputMw))
  const path = (points ?? [])
    .map((p, i) => {
      const x = (i / Math.max(1, points.length - 1)) * width
      const y = height - (p.outputMw / maxMw) * height
      return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')

  return (
    <ModalShell onClose={onClose}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-sm font-semibold text-slate-900">{plant.name} — forecast</h2>
          <p className="mt-0.5 text-xs text-slate-400">next 12 ticks, {meta.label.toLowerCase()}</p>
        </div>
        <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-600">✕</button>
      </div>

      <div className="mt-3 rounded-lg border border-slate-100 p-3">
        {isLoading && <p className="py-8 text-center text-xs text-slate-400">Loading…</p>}
        {isError && <p className="py-8 text-center text-xs text-red-600">Couldn't load forecast.</p>}
        {points?.length > 0 && (
          <svg viewBox={`0 0 ${width} ${height}`} className="w-full" style={{ height: 120 }}>
            <path d={path} fill="none" stroke={meta.color} strokeWidth="2" />
          </svg>
        )}
        {points?.length > 0 && (
          <div className="mt-2 flex justify-between text-[10px] text-slate-400">
            <span>tick {points[0].tickNumber}</span>
            <span>tick {points[points.length - 1].tickNumber}</span>
          </div>
        )}
      </div>
    </ModalShell>
  )
}

function AddZoneModal({ onClose }) {
  const createZone = useCreateZone(onClose)

  function handleSubmit(e) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    createZone.mutate({ zoneId: form.get('zoneId'), name: form.get('name') })
  }

  return (
    <ModalShell onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <h2 className="text-sm font-semibold text-slate-900">Add zone</h2>
        <p className="mt-0.5 text-xs text-slate-400">Houses, factories and commercial buildings go inside it next.</p>

        <div className="mt-4 grid grid-cols-2 gap-2">
          <label className="block text-xs text-slate-500">
            Zone ID
            <input name="zoneId" required placeholder="Z-SOUTH" className={inputClass.replace('tabular-nums', '')} />
          </label>
          <label className="block text-xs text-slate-500">
            Name
            <input name="name" required className={inputClass.replace('tabular-nums', '')} />
          </label>
        </div>

        <ErrorBox error={createZone.error} />

        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50">
            Cancel
          </button>
          <button
            type="submit"
            disabled={createZone.isPending}
            className="rounded-lg border border-slate-900 bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-40"
          >
            {createZone.isPending ? 'Adding…' : 'Add zone'}
          </button>
        </div>
      </form>
    </ModalShell>
  )
}

const smallInputClass =
  'rounded-lg border border-slate-300 px-2 py-1.5 text-xs text-slate-900 focus:outline-2 focus:outline-sky-500'

/**
 * A zone's detail view: rename or delete the zone itself, and manage the houses, factories and
 * commercial buildings inside it -- add, edit, delete -- all inline, in one modal, rather than
 * stacking a modal on top of a modal for every action.
 */
/**
 * Rename or delete a zone. Its houses/factories/commercial/government units live as their own
 * CustomerNode column now (see AddCustomerModal/UpgradeCustomerModal below), not inside this modal
 * -- so there's nothing left here but the zone container itself.
 */
function ManageZoneModal({ zone, unitCount, onClose }) {
  const [renaming, setRenaming] = useState(false)

  const upgradeZone = useUpgradeZone()
  const deleteZone = useDeleteZone(onClose)

  function handleRename(e) {
    e.preventDefault()
    const name = new FormData(e.currentTarget).get('name')
    upgradeZone.mutate({ zoneId: zone.zoneId, name }, { onSuccess: () => setRenaming(false) })
  }

  function handleDeleteZone() {
    if (window.confirm(`Delete zone ${zone.name} and all ${unitCount} of its customers? This cannot be undone.`)) {
      deleteZone.mutate(zone.zoneId)
    }
  }

  return (
    <ModalShell onClose={onClose}>
      <div className="flex items-start justify-between gap-3">
        {renaming ? (
          <form onSubmit={handleRename} className="flex flex-1 items-center gap-2">
            <input name="name" defaultValue={zone.name} required autoFocus className={`flex-1 ${smallInputClass}`} />
            <button type="submit" className="text-xs font-medium text-sky-600 hover:text-sky-700">Save</button>
            <button type="button" onClick={() => setRenaming(false)} className="text-xs text-slate-400 hover:text-slate-600">
              Cancel
            </button>
          </form>
        ) : (
          <div className="min-w-0">
            <h2 className="truncate text-sm font-semibold text-slate-900">{zone.name}</h2>
            <p className="mt-0.5 text-xs text-slate-400">
              {zone.zoneId} · {unitCount} customer{unitCount === 1 ? '' : 's'}
            </p>
          </div>
        )}
        {!renaming && (
          <div className="flex shrink-0 items-center gap-3">
            <button type="button" onClick={() => setRenaming(true)} className="text-xs font-medium text-slate-500 hover:text-slate-900">
              Rename
            </button>
            <button type="button" onClick={handleDeleteZone} className="text-xs font-medium text-red-500 hover:text-red-600">
              Delete zone
            </button>
            <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-600">✕</button>
          </div>
        )}
      </div>

      <ErrorBox error={upgradeZone.error} />
    </ModalShell>
  )
}

/** Adds a new customer (house/factory/commercial/government building) to a chosen zone -- the
 *  standalone counterpart to AddPlantModal/AddZoneModal, since a customer is now its own node
 *  column rather than something only reachable from inside a zone's own modal. */
function AddCustomerModal({ zones, onClose }) {
  const createUnit = useCreateUnit(onClose)
  const hasZones = Boolean(zones?.length)

  function handleSubmit(e) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    createUnit.mutate({
      unitId: form.get('unitId'),
      zoneId: form.get('zoneId'),
      name: form.get('name'),
      type: form.get('type'),
      capacityKw: Number(form.get('capacityKw')),
    })
  }

  return (
    <ModalShell onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <h2 className="text-sm font-semibold text-slate-900">Add customer</h2>

        <label className="mt-4 block text-xs text-slate-500">
          Zone
          {hasZones ? (
            <select name="zoneId" required defaultValue="" className={inputClass.replace('tabular-nums', '')}>
              <option value="" disabled>Choose a zone…</option>
              {zones.map((z) => <option key={z.zoneId} value={z.zoneId}>{z.name}</option>)}
            </select>
          ) : (
            <span className="mt-1 block rounded-lg border border-dashed border-slate-300 px-2.5 py-1.5 text-slate-400">
              Add a zone first
            </span>
          )}
        </label>

        <div className="mt-3 grid grid-cols-2 gap-2">
          <label className="block text-xs text-slate-500">
            Customer ID
            <input name="unitId" required placeholder="U-NEW" className={inputClass.replace('tabular-nums', '')} />
          </label>
          <label className="block text-xs text-slate-500">
            Name
            <input name="name" required className={inputClass.replace('tabular-nums', '')} />
          </label>
        </div>

        <div className="mt-3 grid grid-cols-2 gap-2">
          <label className="block text-xs text-slate-500">
            Type
            <select name="type" defaultValue="RESIDENTIAL" className={inputClass.replace('tabular-nums', '')}>
              {Object.entries(PROFILE_TYPES).map(([value, m]) => <option key={value} value={value}>{m.label}</option>)}
            </select>
          </label>
          <label className="block text-xs text-slate-500">
            Capacity kW
            <input name="capacityKw" type="number" step="1" min="1" required className={inputClass} />
          </label>
        </div>

        <ErrorBox error={createUnit.error} />

        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50">
            Cancel
          </button>
          <button
            type="submit"
            disabled={!hasZones || createUnit.isPending}
            className="rounded-lg border border-slate-900 bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-40"
          >
            {createUnit.isPending ? 'Adding…' : 'Add customer'}
          </button>
        </div>
      </form>
    </ModalShell>
  )
}

/** Edits a customer's name, type or capacity -- the CustomerNode's "Edit" button. Type can't
 *  change, the same rule UpgradePlantModal already applies (the formula is keyed on it). */
function UpgradeCustomerModal({ unit, onClose }) {
  const upgradeUnit = useUpgradeUnit(onClose)

  function handleSubmit(e) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    upgradeUnit.mutate({
      unitId: unit.unitId,
      zoneId: unit.zoneId,
      name: form.get('name'),
      type: unit.type,
      capacityKw: Number(form.get('capacityKw')),
    })
  }

  return (
    <ModalShell onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <h2 className="text-sm font-semibold text-slate-900">Edit {unit.name}</h2>
        <p className="mt-0.5 text-xs text-slate-400">{PROFILE_TYPES[unit.type]?.label ?? unit.type} · type can't change</p>

        <label className="mt-4 block text-xs text-slate-500">
          Name
          <input name="name" required defaultValue={unit.name} className={inputClass.replace('tabular-nums', '')} />
        </label>

        <label className="mt-3 block text-xs text-slate-500">
          Capacity kW
          <input name="capacityKw" type="number" step="1" min="1" required defaultValue={unit.capacityKw} className={inputClass} />
        </label>

        <ErrorBox error={upgradeUnit.error} />

        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50">
            Cancel
          </button>
          <button
            type="submit"
            disabled={upgradeUnit.isPending}
            className="rounded-lg border border-slate-900 bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-40"
          >
            {upgradeUnit.isPending ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </form>
    </ModalShell>
  )
}

/**
 * "Distributor is just a line zone manager": add, edit and remove the power capacity assigned to
 * each zone. This never touches Customer's own zone list (zones are added/removed there, in
 * ManageZoneModal) -- a capacity here is a billing ceiling keyed to a zone id Customer already
 * owns, not a second copy of the zone itself. Demand above it is never refused (Distributor's own
 * allocation math is unchanged, see DistributionService); Billing surcharges the excess instead --
 * see Billing.billing.BillingCycleService.
 */
function ManageZoneCapacitiesModal({ capacities, zones, onClose }) {
  const [adding, setAdding] = useState(false)
  const [editingZoneId, setEditingZoneId] = useState(null)

  const createCapacity = useCreateZoneCapacity(() => setAdding(false))
  const updateCapacity = useUpdateZoneCapacity(() => setEditingZoneId(null))
  const deleteCapacity = useDeleteZoneCapacity()

  const cappedZoneIds = new Set(capacities.map((c) => c.zoneId))
  const availableZones = zones.filter((z) => !cappedZoneIds.has(z.zoneId))

  function handleAdd(e) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    const zoneId = form.get('zoneId')
    const zone = zones.find((z) => z.zoneId === zoneId)
    createCapacity.mutate({
      zoneId,
      zoneName: zone?.name ?? zoneId,
      capacityKw: Number(form.get('capacityKw')),
    })
  }

  function handleEdit(e, capacity) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    updateCapacity.mutate({
      zoneId: capacity.zoneId,
      zoneName: capacity.zoneName,
      capacityKw: Number(form.get('capacityKw')),
    })
  }

  return (
    <ModalShell onClose={onClose} wide>
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-sm font-semibold text-slate-900">Zone capacities</h2>
          <p className="mt-0.5 text-xs text-slate-400">what Distributor will supply before Billing surcharges the rest</p>
        </div>
        <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-600">✕</button>
      </div>

      <div className="mt-4 max-h-72 overflow-y-auto rounded-lg border border-slate-100">
        {capacities.length === 0 && (
          <p className="px-3 py-4 text-center text-xs text-slate-400">No zone capacities set — every zone is unmetered.</p>
        )}
        {capacities.map((capacity) => {
          if (editingZoneId === capacity.zoneId) {
            return (
              <form
                key={capacity.zoneId}
                onSubmit={(e) => handleEdit(e, capacity)}
                className="border-b border-slate-50 px-3 py-2 last:border-0"
              >
                <div className="flex items-center gap-2">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-xs font-medium text-slate-900">{capacity.zoneName}</p>
                    <p className="text-[10px] text-slate-400">{capacity.zoneId}</p>
                  </div>
                  <input
                    name="capacityKw" type="number" step="1" min="1" required autoFocus
                    defaultValue={capacity.capacityKw} className={`w-28 tabular-nums ${smallInputClass}`}
                  />
                  <span className="text-[10px] text-slate-400">kW</span>
                </div>
                <div className="mt-2 flex justify-end gap-2">
                  <button type="submit" className="text-xs font-medium text-sky-600 hover:text-sky-700">Save</button>
                  <button type="button" onClick={() => setEditingZoneId(null)} className="text-xs text-slate-400 hover:text-slate-600">
                    Cancel
                  </button>
                </div>
                <ErrorBox error={updateCapacity.error} />
              </form>
            )
          }

          const over = capacity.overCapacity
          return (
            <div key={capacity.zoneId} className="flex items-center justify-between gap-3 border-b border-slate-50 px-3 py-2 text-xs last:border-0">
              <div className="min-w-0">
                <p className="truncate font-medium text-slate-900">{capacity.zoneName}</p>
                <p className="text-[10px] text-slate-400">{capacity.zoneId}</p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <span className="tabular-nums font-medium" style={{ color: over ? NEGATIVE : INK }}>
                  {kw(capacity.currentDemandKw)} <span className="font-normal text-slate-400">/ {kw(capacity.capacityKw)}</span>
                </span>
                {over && (
                  <span className="rounded-full px-1.5 py-0.5 text-[9px] font-semibold" style={{ background: '#fee2e2', color: NEGATIVE }}>
                    OVER
                  </span>
                )}
                <button type="button" onClick={() => setEditingZoneId(capacity.zoneId)} className="text-slate-400 hover:text-slate-900">
                  Edit
                </button>
                <button
                  type="button"
                  onClick={() => {
                    if (window.confirm(`Remove the capacity for ${capacity.zoneName}? It will bill at the normal rate only.`)) {
                      deleteCapacity.mutate(capacity.zoneId)
                    }
                  }}
                  className="text-slate-400 hover:text-red-600"
                >
                  Delete
                </button>
              </div>
            </div>
          )
        })}
      </div>

      {adding ? (
        <form onSubmit={handleAdd} className="mt-3 rounded-lg border border-slate-200 p-3">
          {availableZones.length > 0 ? (
            <select name="zoneId" required defaultValue="" className={smallInputClass}>
              <option value="" disabled>Choose a zone…</option>
              {availableZones.map((z) => <option key={z.zoneId} value={z.zoneId}>{z.name}</option>)}
            </select>
          ) : (
            <p className="rounded-lg border border-dashed border-slate-300 px-2.5 py-1.5 text-xs text-slate-400">
              Every zone already has a capacity assigned.
            </p>
          )}
          <input
            name="capacityKw" type="number" step="1" min="1" required placeholder="Capacity kW"
            className={`mt-2 w-full tabular-nums ${smallInputClass}`}
          />
          <ErrorBox error={createCapacity.error} />
          <div className="mt-2 flex justify-end gap-2">
            <button type="button" onClick={() => setAdding(false)} className="text-xs text-slate-400 hover:text-slate-600">
              Cancel
            </button>
            <button
              type="submit"
              disabled={createCapacity.isPending || availableZones.length === 0}
              className="rounded-lg border border-slate-900 bg-slate-900 px-3 py-1 text-xs font-medium text-white hover:bg-slate-700 disabled:opacity-40"
            >
              {createCapacity.isPending ? 'Adding…' : 'Add zone'}
            </button>
          </div>
        </form>
      ) : (
        <button
          type="button"
          onClick={() => setAdding(true)}
          className="mt-3 w-full rounded-lg border border-dashed border-slate-300 py-2 text-xs font-medium text-slate-400 hover:border-slate-400 hover:text-slate-600"
        >
          + Add zone capacity
        </button>
      )}
    </ModalShell>
  )
}

/** Builds one dynamic column of nodes (a fleet of plants, or a fleet of zones) plus a trailing
 *  "add" node -- or, if the owning service can't be reached, a single offline placeholder. Both
 *  columns in this dashboard share this exact shape, just with different node content. */
function buildColumn(items, { offlineId, offlineLabel, addLabel, onAdd, toNode }) {
  if (!items) {
    return [{ id: offlineId, kind: 'offline', label: offlineLabel }]
  }
  return [...items.map(toNode), { id: `add-${offlineId}`, kind: 'add', label: addLabel, onClick: onAdd }]
}

export default function Dashboard() {
  const narrow = useNarrowViewport()
  const [showAddPlant, setShowAddPlant] = useState(false)
  const [showAddStorage, setShowAddStorage] = useState(false)
  const [showAddZone, setShowAddZone] = useState(false)
  const [showAddCustomer, setShowAddCustomer] = useState(false)
  const [upgradingPlant, setUpgradingPlant] = useState(null)
  const [upgradingStorage, setUpgradingStorage] = useState(null)
  const [historyPlant, setHistoryPlant] = useState(null)
  const [forecastPlant, setForecastPlant] = useState(null)
  const [managingZone, setManagingZone] = useState(null)
  const [editingCustomer, setEditingCustomer] = useState(null)
  const [managingCapacities, setManagingCapacities] = useState(false)
  // A rolling client-side window of "did the grid meet demand this tick" samples -- there's no
  // session to score a real uptime percentage against, so this is the closest live proxy: the
  // share of recently-seen ticks where loadExceeded was false. Capped so a long-running tab
  // doesn't grow this unbounded.
  const [reliabilitySamples, setReliabilitySamples] = useState([])
  // Nodes are laid out fresh from live data on every render (positions below are all computed),
  // so a plain drag would just snap back on the next refetch. Keyed by node id and applied as an
  // override at the very end of layout, so a moved node keeps the spot it was dragged to no
  // matter how often the data underneath it changes.
  const [positionOverrides, setPositionOverrides] = useState({})
  // React Flow fires a 'position' change on every pointermove of an active drag, not just at the
  // end -- ReactFlow already renders that motion itself internally, independent of this component.
  // Committing every one of those into state here would re-run this whole function (rebuilding
  // every column and edge from live data) on every pixel of movement, which is exactly what made
  // dragging feel stuck. `dragging === false` is the one final change a drag gesture fires when
  // the pointer is released, so this now commits (and re-renders) exactly once per drag.
  const onNodesChange = (changes) => {
    const dropped = changes.filter((c) => c.type === 'position' && c.position && c.dragging === false)
    if (dropped.length === 0) return
    setPositionOverrides((prev) => {
      const next = { ...prev }
      for (const change of dropped) next[change.id] = change.position
      return next
    })
  }
  const deletePlant = useDeletePlant()
  const decommissionPlant = useDecommissionPlant()
  const deleteUnit = useDeleteUnit()
  const deleteStorage = useDeleteStorage()

  const { data: producer, isError: producerErrored } = useStatus()
  const { data: plants, isError: plantsErrored } = usePlants()
  const { data: storageUnits, isError: storageErrored } = useStorageUnits()
  const { data: grid, isError: gridErrored } = useGridStatus()
  // Distributor has no node of its own in this diagram -- see gridNode's "Manage zone capacities"
  // footer, which took over the one piece of Distributor a user actually needs to reach -- but its
  // health still counts toward "N of 5 live" below, since it's still a real running service.
  const { data: distributor, isError: distributorErrored } = useDistributionStatus()
  const { data: zoneCapacities } = useZoneCapacities()
  const { data: demand, isError: demandErrored } = useDemand()
  const { data: zones, isError: zonesErrored } = useZones()
  const { data: units, isError: unitsErrored } = useUnits()
  const { data: wallets, isError: walletsErrored } = useWallets()
  const { data: billingSummary } = useBillingSummary()

  // Re-samples only when the tick actually advances, not on every 1s poll that returns the same one.
  useEffect(() => {
    if (grid?.tickNumber == null) return
    setReliabilitySamples((prev) => [...prev.slice(-199), !grid.loadExceeded])
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [grid?.tickNumber])

  const producerOnline = Boolean(producer) && !producerErrored
  const plantsOnline = Boolean(plants) && !plantsErrored
  // Storage lives on Producer too, so its liveness doesn't get its own slot in "N of 5 live" --
  // that count is about services, and storage isn't one.
  const storageOnline = Boolean(storageUnits) && !storageErrored
  const gridOnline = Boolean(grid) && !gridErrored
  const distributorOnline = Boolean(distributor) && !distributorErrored
  const customerOnline = Boolean(demand) && !demandErrored && Boolean(zones) && !zonesErrored
    && Boolean(units) && !unitsErrored
  const billingOnline = Boolean(wallets) && !walletsErrored
  const onlineCount = [producerOnline, gridOnline, distributorOnline, customerOnline, billingOnline]
    .filter(Boolean).length

  const gridBalanceKw = (grid?.totalSupplyKw ?? 0) - (grid?.totalDemandKw ?? 0)
  const totalBalanceRupees = wallets?.reduce((sum, w) => sum + w.balanceRupees, 0) ?? 0
  const lowestBalanceRupees = wallets?.length ? Math.min(...wallets.map((w) => w.balanceRupees)) : 0

  // Shared by ProductionMixBar and GoalsPanel -- both read the same fleet-output totals, computed
  // once here rather than duplicated in each.
  const totalOutputMw = (plants ?? []).reduce((sum, p) => sum + Math.max(0, p.currentOutputMw), 0)
  const renewableOutputMw = (plants ?? [])
    .filter((p) => p.type === 'SOLAR' || p.type === 'WIND')
    .reduce((sum, p) => sum + Math.max(0, p.currentOutputMw), 0)
  const renewableSharePct = totalOutputMw > 0 ? (renewableOutputMw / totalOutputMw) * 100 : 0

  const reliabilityPct = reliabilitySamples.length
    ? (reliabilitySamples.filter(Boolean).length / reliabilitySamples.length) * 100
    : 100
  const goalsData = { plantCount: plants?.length ?? 0, renewableSharePct, customerCount: units?.length ?? 0 }
  const goalsCompletedPct = (GOALS.filter((g) => g.current(goalsData) >= g.target).length / GOALS.length) * 100
  const costEfficiencyLabel = billingSummary && billingSummary.spendRupees > 0
    ? `${(billingSummary.revenueRupees / billingSummary.spendRupees).toFixed(1)}x`
    : billingSummary && billingSummary.revenueRupees > 0 ? '∞' : '—'

  // Demand per zone (customer counts, live kW) comes from /api/demand; full zone configuration
  // (profile shape, variability) needed to pre-fill an edit form comes from /api/zones. Joined
  // here by zoneId so each zone node has both without either query knowing about the other.
  const demandByZone = new Map((demand?.zones ?? []).map((z) => [z.zoneId, z]))

  const rowHeight = narrow ? NARROW_ROW : WIDE_COLUMN_ROW

  const plantColumn = buildColumn(plantsOnline ? plants : null, {
    offlineId: 'producer',
    offlineLabel: 'Producer',
    addLabel: 'Add plant',
    onAdd: () => setShowAddPlant(true),
    toNode: (p) => ({
      id: `plant-${p.id}`,
      kind: 'plant',
      data: {
        name: p.name,
        plantType: p.type,
        active: p.active,
        currentOutputMw: p.currentOutputMw,
        capacityMw: p.capacityMw,
        onUpgrade: () => setUpgradingPlant(p),
        onViewHistory: () => setHistoryPlant(p),
        onViewForecast: p.type !== 'THERMAL' ? () => setForecastPlant(p) : undefined,
        // Decommission (Billing refund) before delete (Producer), mirroring how purchase pays
        // Billing before creating in Producer -- a partial refund only makes sense for a plant
        // that still exists to be valued, so the credit has to land first.
        onDelete: async () => {
          if (!window.confirm(`Decommission ${p.name}? This cannot be undone.`)) return
          try {
            await decommissionPlant.mutateAsync({ plantType: p.type, capacityMw: p.capacityMw })
          } catch {
            // A failed refund credit (network/validation) should not still delete the plant.
            return
          }
          deletePlant.mutate(p.id)
        },
      },
    }),
  })

  const storageColumn = buildColumn(storageOnline ? storageUnits : null, {
    offlineId: 'storage-offline',
    offlineLabel: 'Storage',
    addLabel: 'Add storage',
    onAdd: () => setShowAddStorage(true),
    toNode: (s) => ({
      id: `storage-${s.id}`,
      kind: 'storage',
      data: {
        name: s.name,
        kind: s.kind,
        active: s.active,
        capacityKwh: s.capacityKwh,
        stateOfChargeKwh: s.stateOfChargeKwh,
        onUpgrade: () => setUpgradingStorage(s),
        onDelete: () => {
          if (window.confirm(`Delete ${s.name}? This cannot be undone.`)) deleteStorage.mutate(s.id)
        },
      },
    }),
  })

  const zoneById = new Map((zones ?? []).map((z) => [z.zoneId, z]))

  const zoneColumn = buildColumn(customerOnline ? zones : null, {
    offlineId: 'zones-offline',
    offlineLabel: 'Zones',
    addLabel: 'Add zone',
    onAdd: () => setShowAddZone(true),
    toNode: (z) => ({
      id: `zone-${z.zoneId}`,
      kind: 'zone',
      data: {
        name: z.name,
        unitCount: demandByZone.get(z.zoneId)?.unitCount ?? 0,
        demandKw: demandByZone.get(z.zoneId)?.demandKw ?? 0,
        onManage: () => setManagingZone(z),
      },
    }),
  })

  const customerColumn = buildColumn(customerOnline ? units : null, {
    offlineId: 'customers-offline',
    offlineLabel: 'Customers',
    addLabel: 'Add customer',
    onAdd: () => setShowAddCustomer(true),
    toNode: (u) => ({
      id: `customer-${u.unitId}`,
      kind: 'customer',
      data: {
        name: u.name,
        profileType: u.type,
        zoneName: zoneById.get(u.zoneId)?.name ?? u.zoneId,
        capacityKw: u.capacityKw,
        demandKw: u.demandKw,
        onEdit: () => setEditingCustomer(u),
        onDelete: () => {
          if (window.confirm(`Delete ${u.name}?`)) deleteUnit.mutate(u.unitId)
        },
      },
    }),
  })

  // Vertically centers Grid against the plant column, and Billing against the zone column, so a
  // fleet of three and a fleet of thirty both read as balanced, not lopsided.
  const centerAgainst = (columnLength) => Math.max(0, (columnLength * rowHeight) / 2 - 70)

  const columnNode = (item, index, x) => ({
    id: item.id,
    type: item.kind === 'add' ? 'add' : item.kind === 'offline' ? 'service' : item.kind,
    position: narrow ? { x: 0, y: index * rowHeight } : { x, y: index * rowHeight },
    style: ['add', 'plant', 'storage', 'zone', 'customer'].includes(item.kind) ? { pointerEvents: 'auto' } : undefined,
    data:
      item.kind === 'add'
        ? { onClick: item.onClick, label: item.label }
        : item.kind === 'offline'
          ? { label: item.label, subtitle: '', online: false, target: true, source: true, narrow, stats: [] }
          : { ...item.data, narrow, target: true, source: true },
  })

  const plantNodes = plantColumn.map((item, index) => columnNode(item, index, 0))
  const storageNodes = storageColumn.map((item, index) =>
    columnNode(item, narrow ? plantColumn.length + index : index, -340),
  )

  const gridY = narrow ? 0 : centerAgainst(plantColumn.length)
  // Both supply-side columns (plants, storage) stack before Grid in narrow mode.
  const afterPlantsIndex = narrow ? plantColumn.length + storageColumn.length : 0
  const gridNode = {
    id: 'grid',
    type: 'service',
    position: narrow ? { x: 0, y: afterPlantsIndex * rowHeight } : { x: 340, y: gridY },
    // Interactive now (the "manage zone capacities" footer button, moved here from the Distributor
    // node this diagram no longer shows) -- needs the same pointerEvents override columnNode
    // already applies to plant/zone/customer/add nodes, or elementsSelectable={false} on the
    // ReactFlow below swallows its clicks.
    style: { pointerEvents: 'auto' },
    data: {
      label: 'Grid',
      subtitle: `${grid?.simulatedTime ?? '--:--'} · day ${grid?.simulatedDay ?? 0}`,
      online: gridOnline,
      target: true,
      source: true,
      narrow,
      stats: [
        {
          label: 'Frequency Δ',
          value: hz(grid?.frequencyDeviation ?? 0),
          tone: (grid?.frequencyDeviation ?? 0) < -0.01 ? 'negative' : (grid?.frequencyDeviation ?? 0) > 0.01 ? 'positive' : undefined,
        },
        { label: 'Balance', value: kw(gridBalanceKw), tone: gridBalanceKw < 0 ? 'negative' : gridBalanceKw > 0 ? 'positive' : undefined },
        { label: 'Control', value: grid?.autoControlEnabled ? 'Automatic' : 'Manual' },
        {
          label: 'Status',
          value: grid?.loadExceeded ? 'Overloaded' : 'OK',
          tone: grid?.loadExceeded ? 'negative' : 'positive',
        },
      ],
      onManage: () => setManagingCapacities(true),
      manageLabel: 'Manage zone capacities',
    },
  }

  const zonesStartIndex = afterPlantsIndex + 1
  const zoneNodes = zoneColumn.map((item, index) =>
    columnNode(item, narrow ? zonesStartIndex + index : index, 680),
  )

  const customersStartIndex = zonesStartIndex + zoneColumn.length
  const customerNodes = customerColumn.map((item, index) =>
    columnNode(item, narrow ? customersStartIndex + index : index, 1020),
  )

  const billingY = narrow ? 0 : centerAgainst(customerColumn.length)
  const billingIndex = customersStartIndex + customerColumn.length
  const billingNode = {
    id: 'billing',
    type: 'service',
    position: narrow ? { x: 0, y: billingIndex * rowHeight } : { x: 1360, y: billingY },
    data: {
      label: 'Billing',
      subtitle: 'reactive billing, no clock of its own',
      online: billingOnline,
      target: true,
      narrow,
      stats: [
        { label: 'Total balance', value: rupees(totalBalanceRupees) },
        { label: 'Lowest balance', value: rupees(lowestBalanceRupees), tone: lowestBalanceRupees < 0 ? 'negative' : undefined },
        { label: 'Wallets', value: String(wallets?.length ?? 0) },
      ],
    },
  }

  const nodes = [...plantNodes, ...storageNodes, gridNode, ...zoneNodes, ...customerNodes, billingNode].map((node) =>
    positionOverrides[node.id] ? { ...node, position: positionOverrides[node.id] } : node,
  )

  const plantEdges = plantsOnline
    ? plants.map((p) => flowEdge(`plant-${p.id}-grid`, `plant-${p.id}`, 'grid', undefined, p.active && gridOnline))
    : [flowEdge('producer-grid', 'producer', 'grid', 'generation', false)]

  const storageEdges = storageOnline
    ? storageUnits.map((s) => flowEdge(`storage-${s.id}-grid`, `storage-${s.id}`, 'grid', undefined, s.active && gridOnline))
    : [flowEdge('storage-offline-grid', 'storage-offline', 'grid', undefined, false)]

  const gridZoneEdges = customerOnline
    ? zones.map((z) => flowEdge(`grid-zone-${z.zoneId}`, 'grid', `zone-${z.zoneId}`, undefined, gridOnline))
    : [flowEdge('grid-zones-offline', 'grid', 'zones-offline', 'delivery', false)]

  const zoneCustomerEdges = customerOnline
    ? units.map((u) => flowEdge(`zone-${u.zoneId}-customer-${u.unitId}`, `zone-${u.zoneId}`, `customer-${u.unitId}`, undefined, true))
    : [flowEdge('zones-customers-offline', 'zones-offline', 'customers-offline', 'contains', false)]

  // Drawn from Customer, not Zone: what gets billed is every customer's own consumption, even
  // though Billing's own aggregation still happens at the zone level under the hood (see
  // Billing.billing.BillingCycleService) -- Zone is just the container here, not the thing paying.
  const customerBillingEdges = customerOnline
    ? units.map((u) => flowEdge(`customer-${u.unitId}-billing`, `customer-${u.unitId}`, 'billing', undefined, billingOnline))
    : [flowEdge('customers-offline-billing', 'customers-offline', 'billing', 'consumption', false)]

  const edges = [...plantEdges, ...storageEdges, ...gridZoneEdges, ...zoneCustomerEdges, ...customerBillingEdges]

  return (
    <div className="flex h-screen w-screen flex-col overflow-hidden bg-white text-slate-900">
      <header className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 border-b border-slate-200 px-4 py-3 sm:px-6">
        <div className="flex items-baseline gap-2">
          <h1 className="text-sm font-semibold tracking-tight">Power Grid</h1>
          <span className="hidden text-xs text-slate-400 sm:inline">live system view</span>
        </div>
        <div className="flex items-center gap-4 text-xs text-slate-500">
          <span className="tabular-nums">
            {grid?.simulatedTime ?? '--:--'} <span className="text-slate-400">· tick {grid?.tickNumber ?? 0}</span>
          </span>
          <span className="tabular-nums font-semibold text-slate-900">{rupees(totalBalanceRupees)}</span>
          <span className="flex items-center gap-1.5">
            <span
              className="inline-block h-1.5 w-1.5 rounded-full"
              style={{ background: onlineCount === 5 ? ACCENT : onlineCount === 0 ? OFFLINE : '#f59e0b' }}
            />
            {onlineCount} of 5 live
          </span>
        </div>
      </header>

      <div className="relative flex-1">
        {plantsOnline && <ProductionMixBar plants={plants} />}
        {plantsOnline && customerOnline && (
          <GoalsPanel
            plantCount={plants.length}
            renewableSharePct={renewableSharePct}
            customerCount={units?.length ?? 0}
          />
        )}
        {plantsOnline && customerOnline && billingOnline && (
          <ScoreboardPanel
            reliabilityPct={reliabilityPct}
            renewableSharePct={renewableSharePct}
            goalsCompletedPct={goalsCompletedPct}
            costEfficiencyLabel={costEfficiencyLabel}
          />
        )}
        {/* Keyed on `narrow`: a layout orientation change is a fresh fit, not an incremental one. */}
        <ReactFlow
          key={narrow ? 'narrow' : 'wide'}
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          onNodesChange={onNodesChange}
          colorMode="light"
          fitView
          fitViewOptions={{ padding: 0.3 }}
          nodesDraggable
          nodesConnectable={false}
          elementsSelectable={false}
          proOptions={{ hideAttribution: true }}
        >
          <Background gap={20} color="#f1f5f9" />
          <Controls showInteractive={false} />
        </ReactFlow>
      </div>

      {showAddPlant && <AddPlantModal onClose={() => setShowAddPlant(false)} />}
      {upgradingPlant && <UpgradePlantModal plant={upgradingPlant} onClose={() => setUpgradingPlant(null)} />}
      {showAddStorage && <AddStorageModal onClose={() => setShowAddStorage(false)} />}
      {upgradingStorage && <UpgradeStorageModal unit={upgradingStorage} onClose={() => setUpgradingStorage(null)} />}
      {historyPlant && <PlantHistoryModal plant={historyPlant} onClose={() => setHistoryPlant(null)} />}
      {forecastPlant && <ForecastModal plant={forecastPlant} onClose={() => setForecastPlant(null)} />}
      {showAddZone && <AddZoneModal onClose={() => setShowAddZone(false)} />}
      {managingZone && (
        <ManageZoneModal
          zone={managingZone}
          unitCount={demandByZone.get(managingZone.zoneId)?.unitCount ?? 0}
          onClose={() => setManagingZone(null)}
        />
      )}
      {showAddCustomer && <AddCustomerModal zones={zones ?? []} onClose={() => setShowAddCustomer(false)} />}
      {editingCustomer && <UpgradeCustomerModal unit={editingCustomer} onClose={() => setEditingCustomer(null)} />}
      {managingCapacities && (
        <ManageZoneCapacitiesModal
          capacities={zoneCapacities ?? []}
          zones={zones ?? []}
          onClose={() => setManagingCapacities(false)}
        />
      )}
    </div>
  )
}
