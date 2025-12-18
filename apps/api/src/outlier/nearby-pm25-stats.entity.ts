export class NearbyPm25Stats {
  mean: number | null;
  stddev: number | null;
  count: number;

  constructor(mean: unknown, stddev: unknown, count: unknown) {
    this.mean = mean !== null ? Number(mean) : null;
    this.stddev = stddev !== null ? Number(stddev) : null;
    this.count = count !== null ? Number(count) : 0;
  }
}
