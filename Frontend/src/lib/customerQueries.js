import { useQuery } from '@tanstack/react-query'
import * as customerApi from './customerApi'

export const customerKeys = {
  demand: ['customer', 'demand'],
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
