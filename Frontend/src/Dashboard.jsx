import { useCallback, useEffect, useMemo, useState } from 'react'

import { useStatus, usePlants, useDeletePlant } from './lib/queries'
import { useGridStatus } from './lib/gridQueries'
import { useDistributionStatus, useZoneCapacities } from './lib/distributorQueries'
import { useDemand, useZones, useUnits, useDeleteUnit } from './lib/customerQueries'
import { useWallets, useDecommissionPlant, useUnlocks, useBillingSummary } from './lib/billingQueries'
import { useStorageUnits, useDeleteStorage } from './lib/storageQueries'
import { rupees } from './lib/format'

import { ACCENT, OFFLINE, plantCost } from './game/constants'
import World from './game/World'
import DetailPanel from './game/DetailPanel'
import {
  AddPlantModal, UpgradePlantModal, AddStorageModal, UpgradeStorageModal, AddZoneModal,
  AddCustomerModal, UpgradeCustomerModal, ManageZoneCapacitiesModal,
} from './game/modals'

/** What a decommission refunds -- a preview for the confirm dialog only. The server
 *  (Billing.api.WalletController#decommissionPlant) computes and credits the real amount. */
const DECOMMISSION_REFUND_RATIO = 0.5

const GRID = { kind: 'grid' }

/**
 * The whole app: a map of the grid (plants and storage feeding the Grid hub, which feeds the
 * city's zones and the buildings inside them) above a details panel. This component owns every
 * query, the selection, and the modals; World and DetailPanel are presentation over what it hands
 * them. Selection is just `{kind, id}` -- the panel looks the live record up by id each render, so
 * it stays current without this component copying any record into state.
 */
export default function Dashboard() {
  const [selection, setSelection] = useState(GRID)
  const select = useCallback((kind, id) => setSelection(kind === 'grid' ? GRID : { kind, id }), [])

  const [showAddPlant, setShowAddPlant] = useState(false)
  const [showAddStorage, setShowAddStorage] = useState(false)
  const [showAddZone, setShowAddZone] = useState(false)
  // null = closed; { zoneId } = open, with that zone preselected (undefined = no preselection).
  const [addingUnit, setAddingUnit] = useState(null)
  const [upgradingPlant, setUpgradingPlant] = useState(null)
  const [upgradingStorage, setUpgradingStorage] = useState(null)
  const [editingUnit, setEditingUnit] = useState(null)
  const [managingCapacities, setManagingCapacities] = useState(false)
  // A rolling client-side window of "did the grid meet demand this tick" samples -- there's no
  // session to score a real uptime percentage against, so this is the closest live proxy: the
  // share of recently-seen ticks where loadExceeded was false. Capped so a long-running tab
  // doesn't grow this unbounded.
  const [reliabilitySamples, setReliabilitySamples] = useState([])

  const deletePlant = useDeletePlant()
  const decommissionPlant = useDecommissionPlant()
  const deleteUnit = useDeleteUnit()
  const deleteStorage = useDeleteStorage()

  const { data: producer, isError: producerErrored } = useStatus()
  const { data: rawPlants, isError: plantsErrored } = usePlants()
  const { data: rawStorage, isError: storageErrored } = useStorageUnits()
  const { data: grid, isError: gridErrored } = useGridStatus()
  const { data: distributor, isError: distributorErrored } = useDistributionStatus()
  const { data: zoneCapacities } = useZoneCapacities()
  const { data: demand, isError: demandErrored } = useDemand()
  const { data: rawZones, isError: zonesErrored } = useZones()
  const { data: rawUnits, isError: unitsErrored } = useUnits()
  const { data: wallets, isError: walletsErrored } = useWallets()

  // The services list these in no guaranteed order, and a map you learn by position can't have
  // its tiles reshuffle between polls -- so everything drawn is sorted by its own id, once, here.
  const plants = useMemo(() => rawPlants && [...rawPlants].sort((a, b) => a.id - b.id), [rawPlants])
  const storageUnits = useMemo(() => rawStorage && [...rawStorage].sort((a, b) => a.id - b.id), [rawStorage])
  const zones = useMemo(() => rawZones && [...rawZones].sort((a, b) => a.zoneId.localeCompare(b.zoneId)), [rawZones])
  const units = useMemo(() => rawUnits && [...rawUnits].sort((a, b) => a.unitId.localeCompare(b.unitId)), [rawUnits])
  const { data: billingSummary } = useBillingSummary()
  const { data: unlocks } = useUnlocks()

  // Escape returns to the Grid overview, the same as clicking empty ground on the map.
  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') setSelection(GRID) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

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
  const services = [
    { label: 'Producer', online: producerOnline },
    { label: 'Customer', online: customerOnline },
    { label: 'Grid', online: gridOnline },
    { label: 'Distributor', online: distributorOnline },
    { label: 'Billing', online: billingOnline },
  ]
  const onlineCount = services.filter((s) => s.online).length

  const gridBalanceKw = (grid?.totalSupplyKw ?? 0) - (grid?.totalDemandKw ?? 0)
  const totalBalanceRupees = wallets?.reduce((sum, w) => sum + w.balanceRupees, 0) ?? 0

  const totalOutputMw = (plants ?? []).reduce((sum, p) => sum + Math.max(0, p.currentOutputMw), 0)
  const renewableOutputMw = (plants ?? [])
    .filter((p) => p.type === 'SOLAR' || p.type === 'WIND')
    .reduce((sum, p) => sum + Math.max(0, p.currentOutputMw), 0)
  const renewableSharePct = totalOutputMw > 0 ? (renewableOutputMw / totalOutputMw) * 100 : 0

  const reliabilityPct = reliabilitySamples.length
    ? (reliabilitySamples.filter(Boolean).length / reliabilitySamples.length) * 100
    : 100

  // Joined by zone id so a zone's plot has its buildings, live demand and capacity status without
  // any of the three queries knowing about the others.
  const unitsByZone = useMemo(() => {
    const map = new Map()
    for (const u of units ?? []) {
      if (!map.has(u.zoneId)) map.set(u.zoneId, [])
      map.get(u.zoneId).push(u)
    }
    return map
  }, [units])
  const demandByZone = useMemo(() => new Map((demand?.zones ?? []).map((z) => [z.zoneId, z])), [demand])
  const capacityByZone = useMemo(() => new Map((zoneCapacities ?? []).map((c) => [c.zoneId, c])), [zoneCapacities])

  // `null` for an unreachable service (not an empty list) so the map can say "offline" instead of
  // showing an empty region that looks like "nothing built yet".
  const mapPlants = plantsOnline ? plants : null
  const mapStorage = storageOnline ? storageUnits : null
  const mapZones = customerOnline ? zones : null

  const actions = {
    onSelect: select,
    onEditPlant: setUpgradingPlant,
    onEditStorage: setUpgradingStorage,
    onEditUnit: setEditingUnit,
    onAddUnit: (zoneId) => setAddingUnit({ zoneId }),
    onManageCapacities: () => setManagingCapacities(true),
    // Decommission (Billing refund) before delete (Producer), mirroring how purchase pays Billing
    // before creating in Producer -- a partial refund only makes sense for a plant that still
    // exists to be valued, so the credit has to land first.
    onDeletePlant: async (p) => {
      const refund = rupees(plantCost(p.type, p.capacityMw) * DECOMMISSION_REFUND_RATIO)
      if (!window.confirm(`Decommission ${p.name}? You get back about ${refund}. This cannot be undone.`)) return
      try {
        await decommissionPlant.mutateAsync({ plantType: p.type, capacityMw: p.capacityMw })
      } catch {
        // A failed refund credit (network/validation) should not still delete the plant.
        return
      }
      deletePlant.mutate(p.id)
    },
    onDeleteStorage: (s) => {
      if (window.confirm(`Delete ${s.name}? This cannot be undone.`)) deleteStorage.mutate(s.id)
    },
    onDeleteUnit: (u) => {
      if (window.confirm(`Delete ${u.name}?`)) deleteUnit.mutate(u.unitId)
    },
  }

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

      <World
        grid={grid}
        gridOnline={gridOnline}
        plants={mapPlants}
        storageUnits={mapStorage}
        zones={mapZones}
        unitsByZone={unitsByZone}
        demandByZone={demandByZone}
        capacityByZone={capacityByZone}
        selection={selection}
        onSelect={select}
        onAddPlant={() => setShowAddPlant(true)}
        onAddStorage={() => setShowAddStorage(true)}
        onAddZone={() => setShowAddZone(true)}
        onAddUnit={actions.onAddUnit}
      />

      <DetailPanel
        selection={selection}
        data={{
          plants: mapPlants, storageUnits: mapStorage, zones: mapZones, units: customerOnline ? units : null,
          unitsByZone, demandByZone, capacityByZone,
        }}
        overview={{
          grid, gridOnline, gridBalanceKw, plants, services, billingOnline, totalBalanceRupees,
          summary: billingSummary, unlocks, reliabilityPct, renewableSharePct, unitCount: units?.length ?? 0,
        }}
        actions={actions}
      />

      {showAddPlant && <AddPlantModal onClose={() => setShowAddPlant(false)} />}
      {upgradingPlant && <UpgradePlantModal plant={upgradingPlant} onClose={() => setUpgradingPlant(null)} />}
      {showAddStorage && <AddStorageModal onClose={() => setShowAddStorage(false)} />}
      {upgradingStorage && <UpgradeStorageModal unit={upgradingStorage} onClose={() => setUpgradingStorage(null)} />}
      {showAddZone && <AddZoneModal onClose={() => setShowAddZone(false)} />}
      {addingUnit && (
        <AddCustomerModal zones={zones ?? []} defaultZoneId={addingUnit.zoneId} onClose={() => setAddingUnit(null)} />
      )}
      {editingUnit && <UpgradeCustomerModal unit={editingUnit} onClose={() => setEditingUnit(null)} />}
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
