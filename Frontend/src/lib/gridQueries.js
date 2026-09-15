import { useQuery } from '@tanstack/react-query'
import * as gridApi from './gridApi'

export const gridKeys = {
  status: ['grid', 'status'],
}

/** Polls at the tick cadence — the clock advances every 5 s. */
export function useGridStatus() {
  return useQuery({
    queryKey: gridKeys.status,
    queryFn: gridApi.getGridStatus,
    refetchInterval: 1000,
    retry: false,
  })
}
