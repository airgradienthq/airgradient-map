export class NearbyPm25Stats {
  mean: number | null;
  stddev: number | null;

  constructor(mean: unknown, stddev: unknown) {
    this.mean = mean !== null ? Number(mean) : null;
    this.stddev = stddev !== null ? Number(stddev) : null;
  }
}
