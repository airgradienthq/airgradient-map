import type { FooterLinkGroup } from '~/types';

export const FOOTER_LINKS_CONFIG: FooterLinkGroup[] = [
  {
    group: 1,
    links: [
      {
        label: 'manifesto',
        path: 'https://www.airgradient.com/manifesto/',
        openBlank: true
      },
      {
        label: 'building-consultants',
        path: 'https://www.airgradient.com/building-consultants/',
        openBlank: true
      },
      {
        label: 'educators',
        path: 'https://www.airgradient.com/air-pollution-educators/',
        openBlank: true
      },
      {
        label: 'wildfire-aq',
        path: 'https://www.airgradient.com/wildfire/',
        openBlank: true
      },
      {
        label: 'cigarettes-calculator',
        path: 'https://www.airgradient.com/cigarettes-equivalent-calculator/',
        openBlank: true
      },
      {
        label: 'terms-conditions-quiz',
        path: 'https://www.airgradient.com/aq-data-ownership-quiz/',
        openBlank: true
      }
    ]
  },
  {
    group: 2,
    links: [
      {
        label: 'dashboard',
        path: 'https://app.airgradient.com/dashboard',
        openBlank: true
      },
      {
        label: 'integrations',
        path: 'https://www.airgradient.com/integrations/',
        openBlank: true
      },
      {
        label: 'api',
        path: 'https://api.airgradient.com/public/docs/api/v1/',
        openBlank: true
      },
      {
        label: 'corporate-social',
        path: 'https://www.airgradient.com/scr-initiatives/',
        openBlank: true
      },
      {
        label: 'server-status',
        path: 'https://uptime.airgradient.net/status/servers',
        openBlank: true
      }
    ]
  },
  {
    group: 3,
    links: [
      {
        label: 'privacy-policy',
        path: 'https://www.airgradient.com/privacy-policy/',
        openBlank: true
      },
      {
        label: 'terms-conditions',
        path: 'https://www.airgradient.com/terms-conditions/',
        openBlank: true
      },
      {
        label: 'partner-program',
        path: 'https://www.airgradient.com/partner-program/',
        openBlank: true
      },
      {
        label: 'press-influencers',
        path: 'https://www.airgradient.com/press/',
        openBlank: true
      }
    ]
  }
];
