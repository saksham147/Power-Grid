export const mw = (n) => `${Number(n).toFixed(1)} MW`

export const mwh = (n) =>
  n >= 1000 ? `${(n / 1000).toFixed(2)} GWh` : `${Number(n).toFixed(1)} MWh`
