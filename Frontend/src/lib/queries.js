import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as api from './api'

export const keys = {
  status: ['simulation', 'status'],
  plants: (activeOnly) => ['plants', { activeOnly }],
}

/** Polls only while a run is active — an idle simulation needs no heartbeat. */
export function useStatus() {
  return useQuery({
    queryKey: keys.status,
    queryFn: api.getStatus,
    // The clock advances every 5 s; polling at 1 s keeps it moving visibly without
    // pretending to a resolution the tick does not have.
    refetchInterval: 1000,
    retry: false,
  })
}

export function usePlants(activeOnly) {
  return useQuery({
    queryKey: keys.plants(activeOnly),
    queryFn: () => api.listPlants(activeOnly),
    // Output and energy are written back once per tick, so the table tracks the tick.
    refetchInterval: 5000,
    retry: false,
  })
}

/** Every mutation refreshes both halves of the screen — one tick moves both. */
function useRefreshingMutation(mutationFn, options = {}) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn,
    ...options,
    onSuccess: (data, vars, ctx) => {
      qc.invalidateQueries({ queryKey: keys.status })
      qc.invalidateQueries({ queryKey: ['plants'] })
      options.onSuccess?.(data, vars, ctx)
    },
  })
}

export const useSetDeviation = () => useRefreshingMutation(api.setFrequencyDeviation)
export const useCreatePlant = (onSuccess) => useRefreshingMutation(api.createPlant, { onSuccess })
export const useSetActive = () =>
  useRefreshingMutation(({ id, active }) => api.setPlantActive(id, active))
export const useUpgradePlant = (onSuccess) =>
  useRefreshingMutation(({ id, ...body }) => api.upgradePlant(id, body), { onSuccess })
export const useDeletePlant = () => useRefreshingMutation(api.deletePlant)
