import { useEffect, useState } from 'react'
import { ReactFlow, Background, Controls, Handle, MarkerType, Position } from '@xyflow/react'
import '@xyflow/react/dist/style.css'

import { useStatus, usePlants, useCreatePlant, useUpgradePlant, useDeletePlant, usePlantHistory } from './lib/queries'
import { useGridStatus } from './lib/gridQueries'
import { useDistributionStatus } from './lib/distributorQueries'
import { useDemand } from './lib/customerQueries'
import { mw, mwh, kw, hz, time } from './lib/format'

// The one accent this page allows itself for service state: white and grayscale carry the page,
// this marks whatever is actually live. Plant nodes are the one deliberate exception -- their
// color is functional too, it identifies generation type, not mood.
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

const NARROW_BREAKPOINT = 768
const WIDE_PLANT_ROW = 100
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
        <button type="button" onClick={data.onDelete} className="flex-1 py-1.5 hover:bg-red-50 hover:text-red-600">
          Delete
        </button>
      </div>

      <Handle type="source" position={sourcePosition} style={{ background: dotColor, border: 'none' }} />
    </div>
  )
}

function AddPlantNode({ data }) {
  return (
    <button
      type="button"
      onClick={data.onClick}
      className="flex w-60 cursor-pointer flex-col items-center justify-center gap-1 rounded-2xl border border-dashed border-slate-300 bg-white px-4 py-6 text-slate-400 transition hover:border-slate-400 hover:text-slate-600"
    >
      <span className="text-xl leading-none">+</span>
      <span className="text-xs font-medium">Add plant</span>
    </button>
  )
}

const nodeTypes = { service: ServiceNode, plant: PlantNode, addPlant: AddPlantNode }

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

function AddPlantModal({ onClose }) {
  const createPlant = useCreatePlant(onClose)

  function handleSubmit(e) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    createPlant.mutate({
      name: form.get('name'),
      type: form.get('type'),
      capacityMw: Number(form.get('capacityMw')),
      minOutputMw: Number(form.get('minOutputMw') || 0),
      baseOutputMw: Number(form.get('baseOutputMw') || 0),
    })
  }

  return (
    <div className="fixed inset-0 z-10 flex items-center justify-center bg-slate-900/20 px-4" onClick={onClose}>
      <form
        onClick={(e) => e.stopPropagation()}
        onSubmit={handleSubmit}
        className="w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-5 shadow-lg"
      >
        <h2 className="text-sm font-semibold text-slate-900">Add plant</h2>

        <label className="mt-4 block text-xs text-slate-500">
          Name
          <input
            name="name"
            required
            className="mt-1 w-full rounded-lg border border-slate-300 px-2.5 py-1.5 text-sm text-slate-900 focus:outline-2 focus:outline-sky-500"
          />
        </label>

        <label className="mt-3 block text-xs text-slate-500">
          Type
          <select
            name="type"
            defaultValue="THERMAL"
            className="mt-1 w-full rounded-lg border border-slate-300 px-2.5 py-1.5 text-sm text-slate-900 focus:outline-2 focus:outline-sky-500"
          >
            {Object.entries(PLANT_TYPES).map(([value, meta]) => (
              <option key={value} value={value}>{meta.label}</option>
            ))}
          </select>
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
              className="mt-1 w-full rounded-lg border border-slate-300 px-2 py-1.5 text-sm tabular-nums text-slate-900 focus:outline-2 focus:outline-sky-500"
            />
          </label>
          <label className="block text-xs text-slate-500">
            Min MW
            <input
              name="minOutputMw"
              type="number"
              step="0.1"
              min="0"
              defaultValue="0"
              className="mt-1 w-full rounded-lg border border-slate-300 px-2 py-1.5 text-sm tabular-nums text-slate-900 focus:outline-2 focus:outline-sky-500"
            />
          </label>
          <label className="block text-xs text-slate-500">
            Base MW
            <input
              name="baseOutputMw"
              type="number"
              step="0.1"
              min="0"
              defaultValue="0"
              className="mt-1 w-full rounded-lg border border-slate-300 px-2 py-1.5 text-sm tabular-nums text-slate-900 focus:outline-2 focus:outline-sky-500"
            />
          </label>
        </div>

        {createPlant.error && (
          <div className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
            <p>{createPlant.error.message}</p>
            {createPlant.error.details?.length > 0 && (
              <ul className="mt-1 list-disc pl-4">
                {createPlant.error.details.map((d) => <li key={d}>{d}</li>)}
              </ul>
            )}
          </div>
        )}

        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={createPlant.isPending}
            className="rounded-lg border border-slate-900 bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-40"
          >
            {createPlant.isPending ? 'Adding…' : 'Add plant'}
          </button>
        </div>
      </form>
    </div>
  )
}

function UpgradePlantModal({ plant, onClose }) {
  const upgradePlant = useUpgradePlant(onClose)

  function handleSubmit(e) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    upgradePlant.mutate({
      id: plant.id,
      name: form.get('name'),
      capacityMw: Number(form.get('capacityMw')),
      minOutputMw: Number(form.get('minOutputMw') || 0),
      baseOutputMw: Number(form.get('baseOutputMw') || 0),
    })
  }

  return (
    <div className="fixed inset-0 z-10 flex items-center justify-center bg-slate-900/20 px-4" onClick={onClose}>
      <form
        onClick={(e) => e.stopPropagation()}
        onSubmit={handleSubmit}
        className="w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-5 shadow-lg"
      >
        <h2 className="text-sm font-semibold text-slate-900">Edit {plant.name}</h2>
        <p className="mt-0.5 text-xs text-slate-400">{PLANT_TYPES[plant.type]?.label ?? plant.type} · type can't change</p>

        <label className="mt-4 block text-xs text-slate-500">
          Name
          <input
            name="name"
            required
            defaultValue={plant.name}
            className="mt-1 w-full rounded-lg border border-slate-300 px-2.5 py-1.5 text-sm text-slate-900 focus:outline-2 focus:outline-sky-500"
          />
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
              defaultValue={plant.capacityMw}
              className="mt-1 w-full rounded-lg border border-slate-300 px-2 py-1.5 text-sm tabular-nums text-slate-900 focus:outline-2 focus:outline-sky-500"
            />
          </label>
          <label className="block text-xs text-slate-500">
            Min MW
            <input
              name="minOutputMw"
              type="number"
              step="0.1"
              min="0"
              defaultValue={plant.minOutputMw}
              className="mt-1 w-full rounded-lg border border-slate-300 px-2 py-1.5 text-sm tabular-nums text-slate-900 focus:outline-2 focus:outline-sky-500"
            />
          </label>
          <label className="block text-xs text-slate-500">
            Base MW
            <input
              name="baseOutputMw"
              type="number"
              step="0.1"
              min="0"
              defaultValue={plant.baseOutputMw}
              className="mt-1 w-full rounded-lg border border-slate-300 px-2 py-1.5 text-sm tabular-nums text-slate-900 focus:outline-2 focus:outline-sky-500"
            />
          </label>
        </div>

        {upgradePlant.error && (
          <div className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
            <p>{upgradePlant.error.message}</p>
            {upgradePlant.error.details?.length > 0 && (
              <ul className="mt-1 list-disc pl-4">
                {upgradePlant.error.details.map((d) => <li key={d}>{d}</li>)}
              </ul>
            )}
          </div>
        )}

        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={upgradePlant.isPending}
            className="rounded-lg border border-slate-900 bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-40"
          >
            {upgradePlant.isPending ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </form>
    </div>
  )
}

function PlantHistoryModal({ plant, onClose }) {
  const { data: points, isLoading, isError } = usePlantHistory(plant.id, true)

  return (
    <div className="fixed inset-0 z-10 flex items-center justify-center bg-slate-900/20 px-4" onClick={onClose}>
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-sm rounded-2xl border border-slate-200 bg-white p-5 shadow-lg"
      >
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
            <div
              key={`${point.at}-${i}`}
              className="flex items-center justify-between gap-3 border-b border-slate-50 px-3 py-1.5 text-xs last:border-0"
            >
              <span className="text-slate-500">{time(point.at)}</span>
              <span className="tabular-nums font-medium text-slate-900">{mw(point.outputMw)}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

export default function Dashboard() {
  const narrow = useNarrowViewport()
  const [showAddPlant, setShowAddPlant] = useState(false)
  const [upgradingPlant, setUpgradingPlant] = useState(null)
  const [historyPlant, setHistoryPlant] = useState(null)
  const deletePlant = useDeletePlant()

  const { data: producer, isError: producerErrored } = useStatus()
  const { data: plants, isError: plantsErrored } = usePlants()
  const { data: grid, isError: gridErrored } = useGridStatus()
  const { data: distributor, isError: distributorErrored } = useDistributionStatus()
  const { data: demand, isError: demandErrored } = useDemand()

  const producerOnline = Boolean(producer) && !producerErrored
  const plantsOnline = Boolean(plants) && !plantsErrored
  const gridOnline = Boolean(grid) && !gridErrored
  const distributorOnline = Boolean(distributor) && !distributorErrored
  const customerOnline = Boolean(demand) && !demandErrored
  const onlineCount = [producerOnline, gridOnline, distributorOnline, customerOnline].filter(Boolean).length

  const gridBalanceKw = (grid?.totalSupplyKw ?? 0) - (grid?.totalDemandKw ?? 0)
  const totalCustomers = demand?.zones?.reduce((sum, z) => sum + z.customers, 0) ?? 0

  // The left column is either one plant per row plus an "add" row, or -- if Producer itself
  // can't be reached -- a single offline placeholder, the same way every other service renders
  // when it's unreachable. There is nothing to add to a fleet nobody can currently see.
  const leftColumn = plantsOnline
    ? [...plants.map((p) => ({ kind: 'plant', plant: p })), { kind: 'add' }]
    : [{ kind: 'offline' }]

  const rowHeight = narrow ? NARROW_ROW : WIDE_PLANT_ROW
  const leftColumnHeight = leftColumn.length * rowHeight
  // Vertically centers Grid/Distributor/Customer against however tall the plant column turns
  // out to be, so three plants and thirty both read as a balanced diagram, not a lopsided one.
  const wideCenterY = Math.max(0, leftColumnHeight / 2 - 70)

  const leftNodes = leftColumn.map((item, index) => {
    const position = narrow ? { x: 0, y: index * rowHeight } : { x: 0, y: index * rowHeight }

    if (item.kind === 'plant') {
      const p = item.plant
      return {
        id: `plant-${p.id}`,
        type: 'plant',
        position,
        // Same override as the add-plant node: elementsSelectable={false} disables pointer
        // events on every node by default, and this one carries three real buttons.
        style: { pointerEvents: 'auto' },
        data: {
          name: p.name,
          plantType: p.type,
          active: p.active,
          currentOutputMw: p.currentOutputMw,
          capacityMw: p.capacityMw,
          narrow,
          onUpgrade: () => setUpgradingPlant(p),
          onViewHistory: () => setHistoryPlant(p),
          onDelete: () => {
            if (window.confirm(`Delete ${p.name}? This cannot be undone.`)) {
              deletePlant.mutate(p.id)
            }
          },
        },
      }
    }
    if (item.kind === 'add') {
      // elementsSelectable={false} sets pointer-events: none on every node wrapper -- this is
      // the one node that needs a working click, so its own style overrides that back to auto.
      return {
        id: 'add-plant',
        type: 'addPlant',
        position,
        style: { pointerEvents: 'auto', cursor: 'pointer' },
        data: { onClick: () => setShowAddPlant(true) },
      }
    }
    return {
      id: 'producer',
      type: 'service',
      position,
      data: {
        label: 'Producer',
        subtitle: `Tick ${producer?.tickNumber ?? 0} · ${producer?.simulatedTime ?? '--:--'}`,
        online: false,
        source: true,
        narrow,
        stats: [
          { label: 'Output', value: mw(0) },
          { label: 'Energy', value: mwh(0) },
          { label: 'Active plants', value: '0' },
        ],
      },
    }
  })

  const fixedStartIndex = narrow ? leftColumn.length : 0
  const fixedPosition = (offset) =>
    narrow ? { x: 0, y: (fixedStartIndex + offset) * rowHeight } : { x: 340 + offset * 340, y: wideCenterY }

  const otherNodes = [
    {
      id: 'grid',
      type: 'service',
      position: fixedPosition(0),
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
        ],
      },
    },
    {
      id: 'distributor',
      type: 'service',
      position: fixedPosition(1),
      data: {
        label: 'Distributor',
        subtitle: 'reactive merge, no clock of its own',
        online: distributorOnline,
        target: true,
        source: true,
        narrow,
        stats: [
          { label: 'Supply tracked', value: kw(distributor?.totalSupplyKw ?? 0) },
          { label: 'Demand tracked', value: kw(distributor?.totalDemandKw ?? 0) },
          { label: 'Zones tracked', value: String(distributor?.trackedZoneCount ?? 0) },
        ],
      },
    },
    {
      id: 'customer',
      type: 'service',
      position: fixedPosition(2),
      data: {
        label: 'Customer',
        subtitle: `Tick ${demand?.tick ?? 0} · ${demand?.simulatedTime ?? '--:--'}`,
        online: customerOnline,
        target: true,
        narrow,
        stats: [
          { label: 'Total demand', value: kw(demand?.totalKw ?? 0) },
          { label: 'Zones', value: String(demand?.zones?.length ?? 0) },
          { label: 'Population', value: totalCustomers.toLocaleString() },
        ],
      },
    },
  ]

  const nodes = [...leftNodes, ...otherNodes]

  const plantEdges = plantsOnline
    ? plants.map((p) => flowEdge(`plant-${p.id}-grid`, `plant-${p.id}`, 'grid', undefined, p.active && gridOnline))
    : [flowEdge('producer-grid', 'producer', 'grid', 'generation', false)]

  const edges = [
    ...plantEdges,
    flowEdge('grid-distributor', 'grid', 'distributor', 'regulated supply', gridOnline && distributorOnline),
    flowEdge('distributor-customer', 'distributor', 'customer', 'delivery', distributorOnline && customerOnline),
  ]

  return (
    <div className="flex h-screen w-screen flex-col overflow-hidden bg-white text-slate-900">
      <header className="flex items-center justify-between border-b border-slate-200 px-4 py-3 sm:px-6">
        <div className="flex items-baseline gap-2">
          <h1 className="text-sm font-semibold tracking-tight">Power Grid</h1>
          <span className="hidden text-xs text-slate-400 sm:inline">live system view</span>
        </div>
        <div className="flex items-center gap-1.5 text-xs text-slate-500">
          <span
            className="inline-block h-1.5 w-1.5 rounded-full"
            style={{ background: onlineCount === 4 ? ACCENT : onlineCount === 0 ? OFFLINE : '#f59e0b' }}
          />
          {onlineCount} of 4 live
        </div>
      </header>

      <div className="flex-1">
        {/* Keyed on `narrow`: a layout orientation change is a fresh fit, not an incremental one. */}
        <ReactFlow
          key={narrow ? 'narrow' : 'wide'}
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          colorMode="light"
          fitView
          fitViewOptions={{ padding: 0.3 }}
          nodesDraggable={false}
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
      {historyPlant && <PlantHistoryModal plant={historyPlant} onClose={() => setHistoryPlant(null)} />}
    </div>
  )
}
