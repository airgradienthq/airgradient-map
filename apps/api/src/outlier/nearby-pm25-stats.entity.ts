export class NearbyPm25Stats {
  mean: number | null;
  stddev: number | null;
  count: number;
  p25: number | null;
  median: number | null;
  p75: number | null;

  constructor(
    mean: unknown,
    stddev: unknown,
    count: unknown,
    p25: unknown,
    median: unknown,
    p75: unknown,
  ) {
    this.mean = mean !== null ? Number(mean) : null;
    this.stddev = stddev !== null ? Number(stddev) : null;
    this.count = count !== null ? Number(count) : 0;
    this.p25 = p25 !== null && p25 !== undefined ? Number(p25) : null;
    this.median = median !== null && median !== undefined ? Number(median) : null;
    this.p75 = p75 !== null && p75 !== undefined ? Number(p75) : null;
  }
}
