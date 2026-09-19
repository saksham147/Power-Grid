// The one accent this page allows itself for service state: white and grayscale carry the page,
// this marks whatever is actually live. Plant, storage and building tiles are the deliberate
// exception -- their color is functional too, it identifies generation type or demand profile.
export const ACCENT = '#0ea5e9'
export const OFFLINE = '#cbd5e1'
export const POSITIVE = '#059669'
export const NEGATIVE = '#e11d48'
export const INK = '#0f172a'

export const PLANT_TYPES = {
  THERMAL: { color: '#f97316', label: 'Thermal' },
  SOLAR: { color: '#eab308', label: 'Solar' },
  WIND: { color: '#06b6d4', label: 'Wind' },
}

/** What a plant costs to build (or, at the margin, to grow), debited from the shared Grid wallet
 *  before Producer ever sees the request -- see AddPlantModal/UpgradePlantModal. This is a preview
 *  only: the server (Billing.billing.PlantPricing) computes and charges the authoritative number
 *  from the same formula, so this must mirror it exactly or the preview lies. cost = max(minimum,
 *  base + progressive per-MW cost): base is the fixed setup cost, minimum is a floor so a tiny
 *  plant is never near-free, and the per-MW cost is banded like a tax bracket -- each band only
 *  charges its own rate on the slice of capacity inside it, rising band to band. Bands are
 *  calibrated against this simulation's real plant sizes -- Thermal up to ~900 MW, Wind ~150 MW,
 *  Solar ~200 MW. */
export const PLANT_RATES = {
  THERMAL: { base: 2000, minimum: 5000, bands: [{ uptoMw: 300, perMw: 8 }, { uptoMw: 600, perMw: 10 }, { uptoMw: Infinity, perMw: 14 }] },
  WIND: { base: 1000, minimum: 3000, bands: [{ uptoMw: 50, perMw: 16 }, { uptoMw: 100, perMw: 20 }, { uptoMw: Infinity, perMw: 26 }] },
  SOLAR: { base: 500, minimum: 2000, bands: [{ uptoMw: 50, perMw: 8 }, { uptoMw: 150, perMw: 10 }, { uptoMw: Infinity, perMw: 13 }] },
}

export function plantCost(type, capacityMw) {
  const { base, minimum, bands } = PLANT_RATES[type]
  const mw = capacityMw || 0

  let bandedCost = 0
  let coveredMw = 0
  for (const band of bands) {
    const mwInBand = Math.max(0, Math.min(mw, band.uptoMw) - coveredMw)
    bandedCost += mwInBand * band.perMw
    coveredMw = band.uptoMw
    if (mw <= band.uptoMw) break
  }

  return Math.max(minimum, base + bandedCost)
}

export const STORAGE_TYPES = {
  BATTERY: { color: '#84cc16', label: 'Battery' },
  HYDROGEN: { color: '#38bdf8', label: 'Hydrogen' },
}

/** Mirrors Billing.billing.StoragePricing exactly -- see PLANT_RATES's own comment for why this
 *  has to match the server's formula rather than approximate it. */
export const STORAGE_RATES = {
  BATTERY: { base: 1500, perKwh: 5, minimum: 3000 },
  HYDROGEN: { base: 3000, perKwh: 8, minimum: 6000 },
}

export function storageCost(kind, capacityKwh) {
  const { base, perKwh, minimum } = STORAGE_RATES[kind]
  return Math.max(minimum, base + perKwh * (capacityKwh || 0))
}

export const PROFILE_TYPES = {
  RESIDENTIAL: { color: '#8b5cf6', label: 'Residential' },
  COMMERCIAL: { color: '#ec4899', label: 'Commercial' },
  INDUSTRIAL: { color: '#14b8a6', label: 'Industrial' },
  GOV: { color: '#f59e0b', label: 'Government' },
}
