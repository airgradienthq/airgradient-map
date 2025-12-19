export class NearbyStats {
  median: number | null;
  scaledMad: number | null;

  constructor(median: unknown, scaledMad: unknown) {
    this.median = median !== null ? Number(median) : null;
    this.scaledMad = scaledMad !== null ? Number(scaledMad) : null;
  }
}
