import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as storageApi from './storageApi'

export const storageKeys = {
  units: ['storage', 'units'],
}

/** stateOfChargeKwh is written back once per tick by StorageCycleService, so this polls at the
 *  same 5 s cadence as usePlants. */
export function useStorageUnits() {
  return useQuery({
    queryKey: storageKeys.units,
    queryFn: storageApi.listStorage,
    refetchInterval: 5000,
    retry: false,
  })
}

function useRefreshingStorageMutation(mutationFn, onSuccess) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (...args) => {
      qc.invalidateQueries({ queryKey: storageKeys.units })
      onSuccess?.(...args)
    },
  })
}

export const useCreateStorage = (onSuccess) => useRefreshingStorageMutation(storageApi.createStorage, onSuccess)
export const useUpgradeStorage = (onSuccess) =>
  useRefreshingStorageMutation(({ id, ...body }) => storageApi.upgradeStorage(id, body), onSuccess)
export const useDeleteStorage = (onSuccess) => useRefreshingStorageMutation(storageApi.deleteStorage, onSuccess)
