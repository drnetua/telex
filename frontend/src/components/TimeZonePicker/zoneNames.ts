/** The city part of an IANA name: `America/Argentina/Buenos_Aires` is "Buenos Aires". */
export function cityOf(zone: string): string {
  return (zone.split("/").pop() ?? zone).replaceAll("_", " ");
}
