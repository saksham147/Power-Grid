import { useState } from 'react'
import { useDeletePlant, useSetActive, useUpgradePlant } from '../lib/queries'
import { Banner, Button, Field } from '../components/ui'
import { mw, mwh } from '../lib/format'

const TONE = { THERMAL: 'bg-orange-500', SOLAR: 'bg-amber-400', WIND: 'bg-teal-500' }

/** Splits "capacityMw: must be greater than 0" into { capacityMw: 'must be…' }. */
function byField(details = []) {
  return Object.fromEntries(
    details.map((d) => {
      const [field, ...rest] = d.split(':')
      return [field.trim(), rest.join(':').trim()]
    }),
  )
}

export default function PlantRow({ plant }) {
  const [mode, setMode] = useState(null) // null | 'edit' | 'confirmRemove'
  const [draft, setDraft] = useState(plant)

  const setActive = useSetActive()
  const remove = useDeletePlant()
  const upgrade = useUpgradePlant(() => setMode(null))

  const busy = setActive.isPending || remove.isPending || upgrade.isPending
  const fieldErrors = byField(upgrade.error?.details)
  const renewable = plant.type !== 'THERMAL'

  function openEditor() {
    setDraft(plant)
    upgrade.reset()
    setMode('edit')
  }

  if (mode === 'edit') {
    return (
      <tr className="align-top">
        <td colSpan={6} className="py-3">
          <div className="rounded-lg border border-slate-300 p-4 dark:border-slate-700">
            <div className="mb-3 flex items-baseline justify-between gap-3">
              <h3 className="text-sm font-semibold">Upgrade {plant.name}</h3>
              <span className="text-xs text-slate-500">
                {plant.type} — type cannot change; it selects the generation strategy
              </span>
            </div>

            {upgrade.error && upgrade.error.details?.length === 0 && (
              <div className="mb-3">
                <Banner error={upgrade.error} />
              </div>
            )}

            <div className="grid gap-3 sm:grid-cols-4">
              <Field
                label="Name"
                value={draft.name}
                error={fieldErrors.name}
                onChange={(e) => setDraft({ ...draft, name: e.target.value })}
              />
              <Field
                label="Capacity (MW)"
                type="number"
                value={draft.capacityMw}
                error={fieldErrors.capacityMw}
                onChange={(e) => setDraft({ ...draft, capacityMw: e.target.value })}
              />
              <Field
                label="Minimum (MW)"
                type="number"
                value={draft.minOutputMw}
                error={fieldErrors.minOutputMw}
                onChange={(e) => setDraft({ ...draft, minOutputMw: e.target.value })}
                hint={renewable ? 'Ignored for this type' : undefined}
              />
              <Field
                label="Base setpoint (MW)"
                type="number"
                value={draft.baseOutputMw}
                error={fieldErrors.baseOutputMw}
                onChange={(e) => setDraft({ ...draft, baseOutputMw: e.target.value })}
                hint={renewable ? 'Ignored for this type' : undefined}
              />
            </div>

            {Number(draft.capacityMw) < plant.currentOutputMw && (
              <p className="mt-3 text-xs text-amber-700 dark:text-amber-400">
                Current output is {mw(plant.currentOutputMw)} — saving will clamp it to the new
                capacity.
              </p>
            )}

            <div className="mt-4 flex gap-2">
              <Button
                variant="primary"
                disabled={upgrade.isPending}
                onClick={() =>
                  upgrade.mutate({
                    id: plant.id,
                    name: draft.name,
                    capacityMw: Number(draft.capacityMw),
                    minOutputMw: Number(draft.minOutputMw),
                    baseOutputMw: Number(draft.baseOutputMw),
                  })
                }
              >
                {upgrade.isPending ? 'Saving…' : 'Save'}
              </Button>
              <Button onClick={() => setMode(null)}>Cancel</Button>
            </div>
          </div>
        </td>
      </tr>
    )
  }

  return (
    <tr className={plant.active ? '' : 'opacity-50'}>
      <td className="py-2.5">{plant.name}</td>
      <td className="py-2.5 text-xs text-slate-500">{plant.type}</td>
      <td className="py-2.5 text-right tabular-nums">{mw(plant.currentOutputMw)}</td>
      <td className="py-2.5 text-right tabular-nums text-slate-500">{mwh(plant.energyMwh)}</td>
      <td className="w-36 py-2.5 pl-4">
        <div className="h-1.5 w-full rounded-full bg-slate-200 dark:bg-slate-800">
          <div
            className={`h-1.5 rounded-full ${TONE[plant.type]}`}
            style={{
              width: `${Math.min(100, (plant.currentOutputMw / plant.capacityMw) * 100)}%`,
            }}
          />
        </div>
        <div className="mt-1 text-[11px] tabular-nums text-slate-500">{mw(plant.capacityMw)}</div>
      </td>
      <td className="py-2.5">
        <div className="flex justify-end gap-1.5">
          {mode === 'confirmRemove' ? (
            <>
              <span className="self-center text-xs text-slate-500">Remove permanently?</span>
              <Button
                variant="danger"
                disabled={remove.isPending}
                onClick={() => remove.mutate(plant.id)}
              >
                {remove.isPending ? 'Removing…' : 'Confirm'}
              </Button>
              <Button onClick={() => setMode(null)}>Cancel</Button>
            </>
          ) : (
            <>
              <Button
                disabled={busy}
                onClick={() => setActive.mutate({ id: plant.id, active: !plant.active })}
              >
                {plant.active ? 'Deactivate' : 'Activate'}
              </Button>
              <Button disabled={busy} onClick={openEditor}>
                Upgrade
              </Button>
              <Button variant="danger" disabled={busy} onClick={() => setMode('confirmRemove')}>
                Remove
              </Button>
            </>
          )}
        </div>
        {remove.error && (
          <div className="mt-2">
            <Banner error={remove.error} />
          </div>
        )}
      </td>
    </tr>
  )
}
