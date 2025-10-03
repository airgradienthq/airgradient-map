import {
  registerDecorator,
  ValidationOptions,
  ValidatorConstraint,
  ValidatorConstraintInterface,
  ValidationArguments,
} from 'class-validator';
import { NotificationType } from '../notification.model';

@ValidatorConstraint({ name: 'isValidTimezone', async: false })
export class IsValidTimezoneConstraint implements ValidatorConstraintInterface {
  validate(timezone: string, args: ValidationArguments): boolean {
    const object = args.object as any;

    if (object.alarm_type !== NotificationType.SCHEDULED) {
      return true;
    }

    if (!timezone || typeof timezone !== 'string') {
      return false;
    }

    try {
      // Attempt to create an Intl.DateTimeFormat with the timezone
      // This will throw an error if the timezone is invalid
      new Intl.DateTimeFormat('en-US', { timeZone: timezone });
      return true;
    } catch (error) {
      console.log(error);
      return false;
    }
  }

  defaultMessage(args: ValidationArguments): string {
    const object = args.object as any;

    if (object.alarm_type === 'scheduled') {
      return 'Invalid timezone. Please provide a valid IANA timezone (e.g., America/New_York, Europe/London)';
    }

    return 'Timezone validation skipped for non-scheduled notifications';
  }
}

export function IsValidTimezone(validationOptions?: ValidationOptions) {
  return function (object: object, propertyName: string) {
    registerDecorator({
      target: object.constructor,
      propertyName: propertyName,
      options: validationOptions,
      constraints: [],
      validator: IsValidTimezoneConstraint,
    });
  };
}
