import { create } from 'zustand'

/**
 * Client-only state. Anything the server owns — status, plants — lives in
 * TanStack Query instead, so there is one source of truth per fact.
 */
export const useUi = create((set) => ({
  deviation: -0.1,
  activeOnly: false,

  setDeviation: (deviation) => set({ deviation }),
  toggleActiveOnly: () => set((s) => ({ activeOnly: !s.activeOnly })),
}))
