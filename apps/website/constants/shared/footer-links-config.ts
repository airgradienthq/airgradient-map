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
        label: 'building_consultants',
        path: 'https://www.airgradient.com/building-consultants/',
        openBlank: true
      },
      {
        label: 'educators',
        path: 'https://www.airgradient.com/air-pollution-educators/',
        openBlank: true
      },
      {
        label: 'wildfire_aq',
        path: 'https://www.airgradient.com/wildfire/',
        openBlank: true
      },
      {
        label: 'cigarettes_calculator',
        path: 'https://www.airgradient.com/cigarettes-equivalent-calculator/',
        openBlank: true
      },
      {
        label: 'terms_conditions_quiz',
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
        label: 'corporate_social',
        path: 'https://www.airgradient.com/scr-initiatives/',
        openBlank: true
      },
      {
        label: 'server_status',
        path: 'https://uptime.airgradient.net/status/servers',
        openBlank: true
      }
    ]
  },
  {
    group: 3,
    links: [
      {
        label: 'privacy_policy',
        path: 'https://www.airgradient.com/privacy-policy/',
        openBlank: true
      },
      {
        label: 'terms_conditions',
        path: 'https://www.airgradient.com/terms-conditions/',
        openBlank: true
      },
      {
        label: 'partner_program',
        path: 'https://www.airgradient.com/partner-program/',
        openBlank: true
      },
      {
        label: 'press_influencers',
        path: 'https://www.airgradient.com/press/',
        openBlank: true
      }
    ]
  }
];
