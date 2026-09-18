import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as customerApi from './customerApi'

export const customerKeys = {
  demand: ['customer', 'demand'],
  zones: ['customer', 'zones'],
  units: ['customer', 'units'],
}

/** Ticks every 5 real seconds, same cadence as Producer. */
export function useDemand() {
  return useQuery({
    queryKey: customerKeys.demand,
    queryFn: customerApi.getDemand,
    refetchInterval: 5000,
    retry: false,
  })
}

/** Zone configuration changes rarely compared to demand, but 5 s keeps a fresh add/edit/delete
 *  visible without a manual refresh. */
export function useZones() {
  return useQuery({
    queryKey: customerKeys.zones,
    queryFn: customerApi.listZones,
    refetchInterval: 5000,
    retry: false,
  })
}

/** Every unit, each with its live demand -- see Customer.api.UnitController. */
export function useUnits() {
  return useQuery({
    queryKey: customerKeys.units,
    queryFn: customerApi.listUnits,
    refetchInterval: 5000,
    retry: false,
  })
}

function useRefreshingMutation(mutationFn, onSuccess) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (...args) => {
      qc.invalidateQueries({ queryKey: customerKeys.zones })
      qc.invalidateQueries({ queryKey: customerKeys.units })
      qc.invalidateQueries({ queryKey: customerKeys.demand })
      onSuccess?.(...args)
    },
  })
}

export const useCreateZone = (onSuccess) => useRefreshingMutation(customerApi.createZone, onSuccess)
export const useUpgradeZone = (onSuccess) =>
  useRefreshingMutation(({ zoneId, ...body }) => customerApi.upgradeZone(zoneId, body), onSuccess)
export const useDeleteZone = (onSuccess) => useRefreshingMutation(customerApi.deleteZone, onSuccess)

export const useCreateUnit = (onSuccess) => useRefreshingMutation(customerApi.createUnit, onSuccess)
export const useUpgradeUnit = (onSuccess) =>
  useRefreshingMutation(({ unitId, ...body }) => customerApi.upgradeUnit(unitId, body), onSuccess)
export const useDeleteUnit = (onSuccess) => useRefreshingMutation(customerApi.deleteUnit, onSuccess)
