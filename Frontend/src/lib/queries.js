import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as api from './api'

export const keys = {
  status: ['simulation', 'status'],
  plants: ['plants'],
  history: (id) => ['plants', id, 'history'],
}

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

export function usePlants() {
  return useQuery({
    queryKey: keys.plants,
    queryFn: api.listPlants,
    // Output and energy are written back once per tick, so the fleet list tracks the tick.
    refetchInterval: 5000,
    retry: false,
  })
}

/** Fetched only while a plant's log panel is open — the fleet view never needs it otherwise. */
export function usePlantHistory(id, enabled) {
  return useQuery({
    queryKey: keys.history(id),
    queryFn: () => api.getPlantHistory(id, { limit: 100 }),
    enabled,
    // New rows land once a tick; matches the fleet list's own cadence.
    refetchInterval: 5000,
    retry: false,
  })
}

function useRefreshingPlantMutation(mutationFn, onSuccess) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (...args) => {
      qc.invalidateQueries({ queryKey: keys.plants })
      onSuccess?.(...args)
    },
  })
}

export const useCreatePlant = (onSuccess) => useRefreshingPlantMutation(api.createPlant, onSuccess)
export const useUpgradePlant = (onSuccess) =>
  useRefreshingPlantMutation(({ id, ...body }) => api.upgradePlant(id, body), onSuccess)
export const useDeletePlant = (onSuccess) => useRefreshingPlantMutation(api.deletePlant, onSuccess)
