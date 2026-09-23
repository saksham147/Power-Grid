import { ApiError } from './api'

const JSON_HEADERS = { 'Content-Type': 'application/json' }

async function request(path, options) {
  let res
  try {
    res = await fetch(path, options)
  } catch {
    throw new ApiError('Cannot reach the Billing service.', { status: 0 })
  }

  if (!res.ok) {
    const body = await res.text()
    let parsed
    try {
      parsed = JSON.parse(body)
    } catch {
      parsed = null
    }
    throw new ApiError(parsed?.message ?? `Request failed (${res.status})`, {
      status: res.status,
      details: parsed?.details ?? [],
    })
  }

  return res.json()
}

export const listWallets = () => request('/billing-api/billing/wallets')

/** Buys a new plant against the shared Grid wallet -- a plant belongs to no zone, so there's no
 *  zone to pick. body: {plantType, capacityMw}; no price is sent, the server computes and charges
 *  it from the same formula the UI uses only as a preview. Throws (402) if the wallet can't cover it. */
export const purchasePlant = (body) =>
  request('/billing-api/billing/plants/purchase', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify(body),
  })

/** Charges only the difference between a plant's old and new price. body: {plantType,
 *  oldCapacityMw, newCapacityMw}. A downgrade costs nothing and never risks a 402. */
export const upgradePlantCost = (body) =>
  request('/billing-api/billing/plants/upgrade', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify(body),
  })

/** Credits the Grid wallet a partial refund for a decommissioned plant. body: {plantType,
 *  capacityMw} -- the plant's own values, so the refund is computed off the same price it was
 *  bought at. A credit can never fail, so this never throws for insufficient funds. Called before
 *  Producer's own plant delete, mirroring how a purchase pays Billing before creating in Producer. */
export const decommissionPlant = (body) =>
  request('/billing-api/billing/plants/decommission', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify(body),
  })

/** Which plant types are currently purchasable -- see Billing.billing.UnlockService. Server-side
 *  enforced regardless (purchasePlant rejects a locked type with 403); this is only for the UI to
 *  grey it out ahead of a failed submit. */
export const getUnlocks = () => request('/billing-api/billing/unlocks')

/** Buys a new storage unit against the shared Grid wallet -- same shape as purchasePlant.
 *  body: {kind, capacityKwh}. */
export const purchaseStorage = (body) =>
  request('/billing-api/billing/storage/purchase', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify(body),
  })

/** Revenue vs. spend, all time -- feeds the live scoreboard panel's "cost efficiency" figure. */
export const getBillingSummary = () => request('/billing-api/billing/summary')

/** Where money is moving right now: revenue per second by zone, each plant's running cost per
 *  second, and lifetime plant spend by category -- see Billing.billing.MoneyFlowService. Rates are
 *  averaged over a short wall-clock window server-side, so they don't jump tick to tick. */
export const getMoneyFlow = () => request('/billing-api/billing/flow')
