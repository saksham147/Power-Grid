import { useQuery } from '@tanstack/react-query'
import * as distributorApi from './distributorApi'

export const distributorKeys = {
  status: ['distributor', 'status'],
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
