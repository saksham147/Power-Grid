export const mw = (n) => `${Number(n).toFixed(1)} MW`

export const mwh = (n) =>
  n >= 1000 ? `${(n / 1000).toFixed(2)} GWh` : `${Number(n).toFixed(1)} MWh`

export const time = (iso) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })

export const kw = (n) =>
  n >= 1000 ? `${(n / 1000).toFixed(2)} MW` : `${Number(n).toFixed(1)} kW`

/** Signed, since a deviation's direction (slow vs. fast) is the point. */
export const hz = (n) => `${n > 0 ? '+' : ''}${Number(n).toFixed(3)} Hz`

export const rupees = (n) => `₹${Number(n).toLocaleString('en-IN', { maximumFractionDigits: 0 })}`

const perSecondDigits = (n) => (Math.abs(n) >= 100 ? 0 : 2)

/** A money rate: ₹12.50/s, or ₹1,908/s once it is big enough that paise are noise. */
export const rupeesPerSec = (n) => {
  const digits = perSecondDigits(Number(n))
  return `₹${Number(n).toLocaleString('en-IN', { minimumFractionDigits: digits, maximumFractionDigits: digits })}/s`
}

/** Signed, since whether a net rate is earning or losing is the point. */
export const signedRupeesPerSec = (n) => `${n > 0 ? '+' : ''}${rupeesPerSec(n)}`
