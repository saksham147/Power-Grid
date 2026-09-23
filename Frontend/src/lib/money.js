/**
 * Splits each zone's billed revenue per second across the buildings inside it, in proportion to
 * how much power each one is drawing right now.
 *
 * Billing works at the zone level -- Customer publishes one demand figure per zone, so that is
 * the finest grain a charge is ever recorded at. This is the honest way to get a per-customer
 * number out of it: while a zone is within its capacity a building's own bill is exactly
 * demand x rate, which is the same as its demand share of the zone's revenue; when the zone is
 * over capacity the surcharge is shared out by the same proportions, which is the fair split of a
 * cost nobody can attribute to one building. Either way the buildings of a zone always add up to
 * the zone's revenue, never more or less.
 *
 * @param zoneRevenue Map of zoneId -> revenue per second for that zone
 * @param units       the Customer service's units, each with zoneId, unitId and live demandKw
 * @returns Map of unitId -> revenue per second; 0 for a zone with nothing drawing power
 */
export function revenueByUnit(zoneRevenue, units) {
  const zoneDemand = new Map()
  for (const u of units) {
    zoneDemand.set(u.zoneId, (zoneDemand.get(u.zoneId) ?? 0) + Math.max(0, u.demandKw))
  }

  const byUnit = new Map()
  for (const u of units) {
    const total = zoneDemand.get(u.zoneId) ?? 0
    const share = total > 0 ? Math.max(0, u.demandKw) / total : 0
    byUnit.set(u.unitId, (zoneRevenue.get(u.zoneId) ?? 0) * share)
  }
  return byUnit
}
