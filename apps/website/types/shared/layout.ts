interface LayoutLink {
  label: string;
  path: string;
  openBlank: boolean;
  children?: LayoutLink[];
}

export type HeaderLink = LayoutLink;
export type FooterLink = LayoutLink;

export interface FooterLinkGroup {
  group: number;
  links: FooterLink[];
}
