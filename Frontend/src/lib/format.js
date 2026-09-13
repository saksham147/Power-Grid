export const mw = (n) => `${Number(n).toFixed(1)} MW`

export const mwh = (n) =>
  n >= 1000 ? `${(n / 1000).toFixed(2)} GWh` : `${Number(n).toFixed(1)} MWh`

export const time = (iso) =>
  new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
