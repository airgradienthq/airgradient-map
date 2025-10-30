export class PM25DataPointEntity {
  measuredAt: Date;
  pm25: number;

  constructor(partial: Partial<PM25DataPointEntity>) {
    Object.assign(this, partial);
  }
}
