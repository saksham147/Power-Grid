import { memo } from 'react'

import { mw, kw, hz } from '../lib/format'
import { ACCENT, OFFLINE, NEGATIVE, POSITIVE, PLANT_TYPES, STORAGE_TYPES, PROFILE_TYPES } from './constants'
import { BuildingIcon, SourceIcon, GridIcon, ZoneIcon } from './icons'

// The map: plain DOM tiles in a CSS flex/grid layout -- no canvas, no per-node measurement, no
// animated edges. That is the whole point of this file replacing the old React Flow graph, which
// re-measured and re-rendered every node on every poll. Tiles are memoized on their own data
// object, so a poll that changes one plant re-renders one tile, not the whole map.

const AMBER = '#f59e0b'

const tileBase =
  'relative flex flex-col items-center rounded-xl border bg-white text-center shadow-sm transition '
  + 'hover:-translate-y-0.5 hover:shadow-md focus-visible:outline-2 focus-visible:outline-sky-500'

const selectedRing = 'border-sky-500 ring-2 ring-sky-300'

function Meter({ pct, color }) {
  return (
    <div className="h-1 w-full overflow-hidden rounded-full bg-slate-200">
      <div className="h-full rounded-full" style={{ width: `${Math.max(0, Math.min(100, pct))}%`, background: color }} />
    </div>
  )
}

/** A power plant or storage unit: icon, name, and one meter -- output against rating for a plant,
 *  state of charge for storage. */
const SourceTile = memo(function SourceTile({ kind, item, selected, onSelect }) {
  const isStorage = kind === 'storage'
  const type = isStorage ? item.kind : item.type
  const meta = (isStorage ? STORAGE_TYPES : PLANT_TYPES)[type] ?? { color: OFFLINE, label: type }

  const pct = isStorage
    ? (item.capacityKwh > 0 ? (item.stateOfChargeKwh / item.capacityKwh) * 100 : 0)
    : (item.capacityMw > 0 ? (item.currentOutputMw / item.capacityMw) * 100 : 0)
  const caption = !item.active ? 'off' : isStorage ? `${Math.round(pct)}%` : mw(item.currentOutputMw)
  const spinning = !isStorage && item.active && item.currentOutputMw > 0

  return (
    <button
      type="button"
      title={`${item.name} · ${meta.label}`}
      onClick={(e) => { e.stopPropagation(); onSelect(kind, item.id) }}
      className={`${tileBase} w-[84px] gap-0.5 px-1.5 pb-1.5 pt-2 ${selected ? selectedRing : 'border-slate-200'} ${item.active ? '' : 'opacity-60 grayscale'}`}
    >
      <SourceIcon kind={kind} type={type} size={40} spinning={spinning} />
      <span className="w-full truncate text-[10px] font-semibold text-slate-900">{item.name}</span>
      <span className="text-[9px] tabular-nums text-slate-500">{caption}</span>
      <Meter pct={pct} color={item.active ? meta.color : OFFLINE} />
    </button>
  )
})

/** One house, shop, factory or government building inside a zone. The meter is its live load
 *  against its own rating, turning amber near the ceiling and red past it. */
const BuildingTile = memo(function BuildingTile({ unit, selected, onSelect }) {
  const meta = PROFILE_TYPES[unit.type] ?? { color: OFFLINE, label: unit.type }
  const pct = unit.capacityKw > 0 ? (unit.demandKw / unit.capacityKw) * 100 : 0
  const meterColor = pct >= 100 ? NEGATIVE : pct >= 85 ? AMBER : meta.color

  return (
    <button
      type="button"
      title={`${unit.name} · ${meta.label} · ${kw(unit.demandKw)}`}
      onClick={(e) => { e.stopPropagation(); onSelect('unit', unit.unitId) }}
      className={`${tileBase} w-[52px] gap-0.5 px-1 pb-1 pt-1.5 ${selected ? selectedRing : 'border-slate-200'}`}
    >
      <BuildingIcon type={unit.type} size={30} />
      <Meter pct={pct} color={meterColor} />
    </button>
  )
})

function AddTile({ label, onClick, compact }) {
  return (
    <button
      type="button"
      onClick={(e) => { e.stopPropagation(); onClick() }}
      className={`flex flex-col items-center justify-center gap-0.5 rounded-xl border border-dashed border-slate-300 bg-white/70 text-slate-400 transition hover:border-slate-400 hover:bg-white hover:text-slate-600 ${compact ? 'h-[46px] w-[52px]' : 'h-[88px] w-[84px]'}`}
    >
      <span className="text-lg leading-none">+</span>
      <span className="text-[9px] font-medium">{label}</span>
    </button>
  )
}

function Offline({ label }) {
  return (
    <p className="rounded-xl border border-dashed border-slate-300 bg-white/70 px-3 py-4 text-center text-xs text-slate-400">
      {label} offline
    </p>
  )
}

function ZonePlot({ zone, units, demandKw, overCapacity, selection, onSelect, onAddUnit }) {
  const selected = selection.kind === 'zone' && selection.id === zone.zoneId

  return (
    <section
      className={`rounded-2xl border-2 p-2.5 transition ${selected ? 'border-sky-500 bg-emerald-50' : overCapacity ? 'border-red-300 bg-emerald-50/70' : 'border-emerald-200 bg-emerald-50/60'}`}
    >
      <button
        type="button"
        onClick={(e) => { e.stopPropagation(); onSelect('zone', zone.zoneId) }}
        className="flex w-full items-center gap-2 rounded-lg text-left focus-visible:outline-2 focus-visible:outline-sky-500"
      >
        <ZoneIcon size={20} />
        <span className="truncate text-xs font-semibold text-slate-900">{zone.name}</span>
        {overCapacity && (
          <span className="rounded-full bg-red-100 px-1.5 py-0.5 text-[9px] font-semibold text-red-600">OVER</span>
        )}
        <span className="ml-auto shrink-0 text-[10px] tabular-nums text-slate-500">{kw(demandKw)}</span>
      </button>

      <div className="mt-2 flex flex-wrap gap-1.5">
        {units.map((u) => (
          <BuildingTile
            key={u.unitId}
            unit={u}
            selected={selection.kind === 'unit' && selection.id === u.unitId}
            onSelect={onSelect}
          />
        ))}
        <AddTile compact label="Add" onClick={() => onAddUnit(zone.zoneId)} />
      </div>
    </section>
  )
}

function GridHub({ grid, online, selected, onSelect }) {
  const overloaded = Boolean(grid?.loadExceeded)
  // loadExceeded only says demand beat supply; the opposite -- supply far above demand, frequency
  // pinned high -- is not "balanced" either, so say what it is.
  const surplus = !overloaded && (grid?.frequencyDeviation ?? 0) > 0.05
  const color = !online ? OFFLINE : overloaded ? NEGATIVE : ACCENT

  return (
    <button
      type="button"
      onClick={(e) => { e.stopPropagation(); onSelect('grid') }}
      className={`${tileBase} w-32 shrink-0 self-center gap-0.5 rounded-2xl border-2 px-3 py-3 ${selected ? selectedRing : 'border-slate-200'}`}
    >
      <GridIcon color={color} size={48} />
      <span className="text-sm font-semibold text-slate-900">Grid</span>
      <span className="text-[10px] tabular-nums text-slate-500">{online ? hz(grid.frequencyDeviation) : '—'}</span>
      <span
        className="mt-1 rounded-full px-2 py-0.5 text-[9px] font-semibold"
        style={{
          background: !online ? '#f1f5f9' : overloaded ? '#fee2e2' : surplus ? '#fef3c7' : '#d1fae5',
          color: !online ? '#94a3b8' : overloaded ? NEGATIVE : surplus ? '#b45309' : POSITIVE,
        }}
      >
        {!online ? 'OFFLINE' : overloaded ? 'OVERLOADED' : surplus ? 'SURPLUS' : 'BALANCED'}
      </span>
    </button>
  )
}

/** A short conduit between two regions. Animated only while power is actually flowing -- the CSS
 *  for that lives in index.css and switches itself off under prefers-reduced-motion. */
function PowerLine({ live }) {
  return <div aria-hidden="true" className={`power-line h-7 w-1 shrink-0 self-center lg:h-1 lg:w-9 ${live ? 'power-line-live' : ''}`} />
}

function Region({ title, className = '', children }) {
  return (
    <section className={`min-w-0 rounded-2xl border border-slate-200 bg-white/70 p-3 ${className}`}>
      <h2 className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-slate-400">{title}</h2>
      {children}
    </section>
  )
}

export default function World({
  grid, gridOnline, plants, storageUnits, zones, unitsByZone, demandByZone, capacityByZone,
  selection, onSelect, onAddPlant, onAddStorage, onAddZone, onAddUnit,
}) {
  return (
    <main
      onClick={() => onSelect('grid')}
      className="min-h-0 flex-1 overflow-auto bg-slate-50 [background-image:radial-gradient(#dbe3ee_1px,transparent_1px)] [background-size:20px_20px]"
    >
      <div className="flex min-h-full flex-col items-stretch p-3 sm:p-4 lg:flex-row">
        <Region title="Generation" className="lg:flex-1">
          <p className="mb-1.5 text-[11px] font-medium text-slate-500">Power plants</p>
          {plants ? (
            <div className="flex flex-wrap gap-2">
              {plants.map((p) => (
                <SourceTile
                  key={p.id}
                  kind="plant"
                  item={p}
                  selected={selection.kind === 'plant' && selection.id === p.id}
                  onSelect={onSelect}
                />
              ))}
              <AddTile label="Plant" onClick={onAddPlant} />
            </div>
          ) : (
            <Offline label="Producer" />
          )}

          <p className="mb-1.5 mt-4 text-[11px] font-medium text-slate-500">Storage</p>
          {storageUnits ? (
            <div className="flex flex-wrap gap-2">
              {storageUnits.map((s) => (
                <SourceTile
                  key={s.id}
                  kind="storage"
                  item={s}
                  selected={selection.kind === 'storage' && selection.id === s.id}
                  onSelect={onSelect}
                />
              ))}
              <AddTile label="Storage" onClick={onAddStorage} />
            </div>
          ) : (
            <Offline label="Storage" />
          )}
        </Region>

        <PowerLine live={gridOnline && Boolean(plants?.some((p) => p.active))} />
        <GridHub grid={grid} online={gridOnline} selected={selection.kind === 'grid'} onSelect={onSelect} />
        <PowerLine live={gridOnline && Boolean(zones?.length)} />

        <Region title="City" className="lg:flex-[1.7]">
          {zones ? (
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              {zones.map((z) => (
                <ZonePlot
                  key={z.zoneId}
                  zone={z}
                  units={unitsByZone.get(z.zoneId) ?? []}
                  demandKw={demandByZone.get(z.zoneId)?.demandKw ?? 0}
                  overCapacity={Boolean(capacityByZone.get(z.zoneId)?.overCapacity)}
                  selection={selection}
                  onSelect={onSelect}
                  onAddUnit={onAddUnit}
                />
              ))}
              <button
                type="button"
                onClick={(e) => { e.stopPropagation(); onAddZone() }}
                className="flex min-h-[88px] items-center justify-center gap-1 rounded-2xl border-2 border-dashed border-slate-300 bg-white/60 text-xs font-medium text-slate-400 transition hover:border-slate-400 hover:text-slate-600"
              >
                <span className="text-lg leading-none">+</span> Add zone
              </button>
            </div>
          ) : (
            <Offline label="Customer" />
          )}
        </Region>
      </div>
    </main>
  )
}
