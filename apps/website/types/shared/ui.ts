export interface DropdownOption {
  label: string;
  value: string | number;
}

export enum DropdownSize {
  SMALL = 'small',
  NORMAL = 'normal'
}

export enum ButtonSize {
  SMALL = 'small',
  NORMAL = 'normal'
}

export enum ButtonColor {
  PRIMARY = 'primary',
  SECONDARY = 'secondary',
  SECONDARY_DARK_BG = 'secondary-dark-bg'
}

export enum DialogSize {
  S = 's',
  M = 'm',
  L = 'l',
  XL = 'xl'
}

export enum ColorsLegendSize {
  SMALL = 'small',
  MEDIUM = 'medium'
}

export enum MarkersLegendSize {
  SMALL = 'small',
  MEDIUM = 'medium'
}

export enum ToastType {
  SUCCESS = 'success',
  ERROR = 'error',
  WARNING = 'warning',
  INFO = 'info'
}

export interface Toast {
  message: string;
  type: ToastType;
  show: boolean;
}
