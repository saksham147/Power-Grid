import { useState } from 'react'
import { useCreatePlant, usePlants } from '../lib/queries'
import { useUi } from '../lib/store'
import { Banner, Button, Card, Field, Stat } from '../components/ui'
import { mw, mwh } from '../lib/format'
import { isUnreachable } from '../lib/api'
import PlantRow from './PlantRow'

const TYPES = ['THERMAL', 'SOLAR', 'WIND']
const EMPTY = { name: '', type: 'THERMAL', capacityMw: '', minOutputMw: '', baseOutputMw: '' }

export default function Plants() {
  const { activeOnly, toggleActiveOnly } = useUi()
  const { data: plants, error, isLoading } = usePlants(activeOnly)
  const [form, setForm] = useState(EMPTY)
  const create = useCreatePlant(() => setForm(EMPTY))

  // The backend sends details as "capacityMw: must be greater than 0".
  const fieldErrors = Object.fromEntries(
    (create.error?.details ?? []).map((d) => {
      const [field, ...rest] = d.split(':')
      return [field.trim(), rest.join(':').trim()]
    }),
  )

  const total = (key) => (plants ?? []).reduce((sum, p) => sum + p[key], 0)
  const renewable = form.type !== 'THERMAL'

  return (
    <div className="space-y-5">
      <Card
        title="Fleet"
        action={
          <Button onClick={toggleActiveOnly}>
            {activeOnly ? 'Showing active' : 'Showing all'}
          </Button>
        }
      >
        {error && !isUnreachable(error) && <Banner error={error} />}

        {isLoading && <p className="text-sm text-slate-500">Loading fleet…</p>}
        {plants?.length === 0 && (
          <p className="text-sm text-slate-500">
            No plants yet. Add one below — a run with an empty fleet publishes nothing.
          </p>
        )}

        {plants?.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wider text-slate-500 dark:border-slate-800">
                    <th className="py-2 font-medium">Plant</th>
                    <th className="py-2 font-medium">Type</th>
                    <th className="py-2 text-right font-medium">Output</th>
                    <th className="py-2 text-right font-medium">Energy</th>
                    <th className="py-2 pl-4 font-medium">of capacity</th>
                    <th className="py-2 text-right font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800/70">
                  {plants.map((p) => (
                    <PlantRow key={p.id} plant={p} />
                  ))}
                </tbody>
              </table>
            </div>

            <div className="mt-4 grid grid-cols-2 gap-4 border-t sm:grid-cols-4 border-slate-200 pt-4 dark:border-slate-800">
              <Stat
                label="Plants"
                value={`${plants.filter((p) => p.active).length} of ${plants.length} active`}
              />
              <Stat label="Generating" value={mw(total('currentOutputMw'))} />
              <Stat label="Energy" value={mwh(total('energyMwh'))} />
              <Stat label="Capacity" value={mw(total('capacityMw'))} />
            </div>
          </>
        )}
      </Card>

      <Card title="Add a plant">
        <form
          className="grid gap-3 sm:grid-cols-3"
          onSubmit={(e) => {
            e.preventDefault()
            create.mutate({
              name: form.name,
              type: form.type,
              capacityMw: Number(form.capacityMw),
              minOutputMw: Number(form.minOutputMw || 0),
              baseOutputMw: Number(form.baseOutputMw || 0),
            })
          }}
        >
          <Field
            label="Name"
            className="sm:col-span-2"
            value={form.name}
            error={fieldErrors.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
          />

          <label className="block">
            <span className="block text-xs font-medium text-slate-600 dark:text-slate-400">
              Type
            </span>
            <select
              className="mt-1 w-full rounded-lg border border-slate-300 bg-transparent px-2.5 py-1.5 text-sm focus:outline-2 focus:outline-sky-500 dark:border-slate-700"
              value={form.type}
              onChange={(e) => setForm({ ...form, type: e.target.value })}
            >
              {TYPES.map((t) => (
                <option key={t} value={t} className="dark:bg-slate-900">
                  {t}
                </option>
              ))}
            </select>
          </label>

          <Field
            label="Capacity (MW)"
            type="number"
            value={form.capacityMw}
            error={fieldErrors.capacityMw}
            onChange={(e) => setForm({ ...form, capacityMw: e.target.value })}
          />
          <Field
            label="Minimum (MW)"
            type="number"
            value={form.minOutputMw}
            error={fieldErrors.minOutputMw}
            onChange={(e) => setForm({ ...form, minOutputMw: e.target.value })}
            hint={renewable ? 'Ignored for this type' : undefined}
          />
          <Field
            label="Base setpoint (MW)"
            type="number"
            value={form.baseOutputMw}
            error={fieldErrors.baseOutputMw}
            onChange={(e) => setForm({ ...form, baseOutputMw: e.target.value })}
            hint={renewable ? 'Ignored for this type' : undefined}
          />

          <div className="sm:col-span-3">
            {renewable && (
              <p className="mb-3 text-xs text-slate-500">
                {form.type === 'SOLAR' ? 'Solar' : 'Wind'} output is computed from capacity alone —
                minimum and setpoint are stored but never read.
              </p>
            )}
            {create.error && create.error.details?.length === 0 && (
              <div className="mb-3">
                <Banner error={create.error} />
              </div>
            )}
            <Button variant="primary" type="submit" disabled={create.isPending}>
              {create.isPending ? 'Adding…' : 'Add plant'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
