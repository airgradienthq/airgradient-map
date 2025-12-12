export class NearbyStats {
  firstCircleMean: number | null;
  firstCircleStddev: number | null;
  secondCircleMean: number | null;
  secondCircleStddev: number | null;

  constructor(
    firstCircleMean: unknown,
    firstCircleStddev: unknown,
    secondCircleMean: unknown,
    secondCircleStddev: unknown,
  ) {
    this.firstCircleMean = firstCircleMean !== null ? Number(firstCircleMean) : null;
    this.firstCircleStddev = firstCircleStddev !== null ? Number(firstCircleStddev) : null;
    this.secondCircleMean = secondCircleMean !== null ? Number(secondCircleMean) : null;
    this.secondCircleStddev = secondCircleStddev !== null ? Number(secondCircleStddev) : null;
  }
}
