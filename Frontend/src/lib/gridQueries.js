import { useQuery } from '@tanstack/react-query'
import * as gridApi from './gridApi'

export const gridKeys = {
  status: ['grid', 'status'],
  history: (limit) => ['grid', 'history', limit],
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

/** Fetched only while the history chart is open — the rest of the dashboard never needs it.
 *  Ticks every 5 s same as the status poll, so a chart left open stays live. */
export function useGridHistory(enabled, limit) {
  return useQuery({
    queryKey: gridKeys.history(limit),
    queryFn: () => gridApi.getGridHistory({ limit }),
    enabled,
    refetchInterval: 5000,
    retry: false,
  })
}
