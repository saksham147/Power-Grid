import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as billingApi from './billingApi'

export const billingKeys = {
  wallets: ['billing', 'wallets'],
  unlocks: ['billing', 'unlocks'],
  summary: ['billing', 'summary'],
  flow: ['billing', 'flow'],
}

/** Billing has no clock of its own either -- see distributorQueries -- so this polls at the same
 *  1 s cadence as Grid, Producer and Distributor rather than the slower 5 s tick cadence. */
export function useWallets() {
  return useQuery({
    queryKey: billingKeys.wallets,
    queryFn: billingApi.listWallets,
    refetchInterval: 1000,
    retry: false,
  })
}

/** Polls at the same cadence as useWallets -- unlocks change only as slowly as cumulative kWh
 *  sold, but there's no separate "something changed" signal, so this just refetches alongside it. */
export function useUnlocks() {
  return useQuery({
    queryKey: billingKeys.unlocks,
    queryFn: billingApi.getUnlocks,
    refetchInterval: 1000,
    retry: false,
  })
}

/** Same shape as usePurchasePlant, for storage. */
export function usePurchaseStorage(onSuccess) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: billingApi.purchaseStorage,
    onSuccess: (...args) => {
      qc.invalidateQueries({ queryKey: billingKeys.wallets })
      onSuccess?.(...args)
    },
  })
}

export function useBillingSummary() {
  return useQuery({
    queryKey: billingKeys.summary,
    queryFn: billingApi.getBillingSummary,
    refetchInterval: 1000,
    retry: false,
  })
}

/** A failed purchase (402 insufficient funds) rejects the mutation rather than silently
 *  succeeding, so a caller must explicitly decide what to do next. The resolved value carries the
 *  server-computed amountRupees actually charged. */
export function usePurchasePlant(onSuccess) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: billingApi.purchasePlant,
    onSuccess: (...args) => {
      qc.invalidateQueries({ queryKey: billingKeys.wallets })
      onSuccess?.(...args)
    },
  })
}

/** Same shape as {@link usePurchasePlant}, for the extra charge an upgrade costs. */
export function useUpgradePlantCost(onSuccess) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: billingApi.upgradePlantCost,
    onSuccess: (...args) => {
      qc.invalidateQueries({ queryKey: billingKeys.wallets })
      onSuccess?.(...args)
    },
  })
}

/** Credits a decommission refund. Never rejects for insufficient funds -- a credit can't fail --
 *  so callers only need to handle network/validation errors. */
export function useDecommissionPlant(onSuccess) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: billingApi.decommissionPlant,
    onSuccess: (...args) => {
      qc.invalidateQueries({ queryKey: billingKeys.wallets })
      onSuccess?.(...args)
    },
  })
}

/** The rates are a 60 s wall-clock average and new charges land once per 5 s tick, so polling faster
 *  than the tick would only re-read the same numbers. */
export function useMoneyFlow() {
  return useQuery({
    queryKey: billingKeys.flow,
    queryFn: billingApi.getMoneyFlow,
    refetchInterval: 5000,
    retry: false,
  })
}
