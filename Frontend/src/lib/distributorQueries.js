import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as distributorApi from './distributorApi'

export const distributorKeys = {
  status: ['distributor', 'status'],
  zoneCapacities: ['distributor', 'zoneCapacities'],
}

/** Distributor has no clock of its own; it reacts to each zone-demand event as it arrives, so this
 *  polls at the same 1 s cadence as Grid and Producer rather than the slower 5 s tick cadence. */
export function useDistributionStatus() {
  return useQuery({
    queryKey: distributorKeys.status,
    queryFn: distributorApi.getDistributionStatus,
    refetchInterval: 1000,
    retry: false,
  })
}

/** Each row carries its own currentDemandKw/overCapacity, live off GridStateTracker -- 1 s cadence
 *  matches useDistributionStatus so the two never visibly disagree. */
export function useZoneCapacities() {
  return useQuery({
    queryKey: distributorKeys.zoneCapacities,
    queryFn: distributorApi.listZoneCapacities,
    refetchInterval: 1000,
    retry: false,
  })
}

function useRefreshingMutation(mutationFn, onSuccess) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (...args) => {
      qc.invalidateQueries({ queryKey: distributorKeys.zoneCapacities })
      onSuccess?.(...args)
    },
  })
}

export const useCreateZoneCapacity = (onSuccess) => useRefreshingMutation(distributorApi.createZoneCapacity, onSuccess)
export const useUpdateZoneCapacity = (onSuccess) =>
  useRefreshingMutation(({ zoneId, ...body }) => distributorApi.updateZoneCapacity(zoneId, body), onSuccess)
export const useDeleteZoneCapacity = (onSuccess) => useRefreshingMutation(distributorApi.deleteZoneCapacity, onSuccess)
