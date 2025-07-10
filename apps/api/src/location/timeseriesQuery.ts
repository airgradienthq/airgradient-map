import { ApiProperty } from '@nestjs/swagger';
import {
  IsString,
  Validate,
  ValidatorConstraint,
  ValidatorConstraintInterface,
  ValidationArguments,
} from 'class-validator';

@ValidatorConstraint({ name: 'IsStartBeforeEnd', async: false })
class IsStartBeforeEndConstraint implements ValidatorConstraintInterface {
  validate(start: string, args: ValidationArguments) {
    const object: any = args.object;
    if (!start || !object.end) return true;
    const startDate = new Date(start);
    const endDate = new Date(object.end);
    return startDate < endDate;
  }
  defaultMessage() {
    return 'Start date must be before end date';
  }
}

class TimeseriesQuery {
  @ApiProperty({ default: '2025-02-01 00:00' })
  @IsString()
  @Validate(IsStartBeforeEndConstraint)
  start: string;

  @ApiProperty({ default: '2025-02-07 00:00' })
  @IsString()
  end: string;

  @ApiProperty({ default: '1 D' })
  @IsString()
  bucketSize: string;
}

export default TimeseriesQuery;
