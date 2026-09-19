import { useState } from 'react'

import { useCreatePlant, useUpgradePlant } from '../lib/queries'
import { useCreateZoneCapacity, useUpdateZoneCapacity, useDeleteZoneCapacity } from '../lib/distributorQueries'
import { useCreateZone, useCreateUnit, useUpgradeUnit } from '../lib/customerQueries'
import { usePurchasePlant, useUpgradePlantCost, useUnlocks, usePurchaseStorage } from '../lib/billingQueries'
import { useCreateStorage, useUpgradeStorage } from '../lib/storageQueries'
import { kw, rupees } from '../lib/format'
import {
  PLANT_TYPES, STORAGE_TYPES, PROFILE_TYPES, plantCost, storageCost, NEGATIVE, INK,
} from './constants'

// Every add/edit form the map opens. Moved here unchanged from the old node-graph Dashboard --
// the forms themselves were never the problem, only the React Flow canvas around them.

/** Shared shell every modal in this file uses: a dimmed backdrop that closes on click, and a
 *  centered white card that doesn't. */
export function ModalShell({ onClose, children, wide }) {
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

export function ErrorBox({ error }) {
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

export const inputClass =
  'mt-1 w-full rounded-lg border border-slate-300 px-2.5 py-1.5 text-sm tabular-nums text-slate-900 focus:outline-2 focus:outline-sky-500'

export function AddPlantModal({ onClose }) {
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

export function UpgradePlantModal({ plant, onClose }) {
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
export function AddStorageModal({ onClose }) {
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

export function UpgradeStorageModal({ unit, onClose }) {
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

export function AddZoneModal({ onClose }) {
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

export const smallInputClass =
  'rounded-lg border border-slate-300 px-2 py-1.5 text-xs text-slate-900 focus:outline-2 focus:outline-sky-500'

/** Adds a new customer (house/factory/commercial/government building) to a chosen zone -- the
 *  standalone counterpart to AddPlantModal/AddZoneModal, since a customer is now its own node
 *  column rather than something only reachable from inside a zone's own modal. */
export function AddCustomerModal({ zones, defaultZoneId, onClose }) {
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
            <select name="zoneId" required defaultValue={defaultZoneId ?? ''} className={inputClass.replace('tabular-nums', '')}>
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
export function UpgradeCustomerModal({ unit, onClose }) {
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
export function ManageZoneCapacitiesModal({ capacities, zones, onClose }) {
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
