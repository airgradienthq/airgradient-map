import type { FooterLinkGroup } from '~/types';

export const FOOTER_LINKS_CONFIG: FooterLinkGroup[] = [
  {
    group: 1,
    links: [
      {
        label: 'Manifesto',
        path: 'https://www.airgradient.com/manifesto/',
        openBlank: false
      },
      {
        label: 'Building Consultants',
        path: 'https://www.airgradient.com/building-consultants/',
        openBlank: false
      },
      {
        label: 'Educators',
        path: 'https://www.airgradient.com/air-pollution-educators/',
        openBlank: false
      },
      {
        label: 'Wildfire AQ',
        path: 'https://www.airgradient.com/wildfire/',
        openBlank: false
      },
      {
        label: 'Cigarettes Equivalent Calculator',
        path: 'https://www.airgradient.com/cigarettes-equivalent-calculator/',
        openBlank: false
      },
      {
        label: 'AQ Terms & Conditions Quiz',
        path: 'https://www.airgradient.com/aq-data-ownership-quiz/',
        openBlank: false
      }
    ]
  },
  {
    group: 2,
    links: [
      {
        label: 'Dashboard',
        path: 'https://app.airgradient.com/dashboard',
        openBlank: true
      },
      {
        label: 'Integrations',
        path: 'https://www.airgradient.com/integrations/',
        openBlank: false
      },
      {
        label: 'API',
        path: 'https://api.airgradient.com/public/docs/api/v1/',
        openBlank: true
      },
      {
        label: 'Corporate Social Responsibility',
        path: 'https://www.airgradient.com/scr-initiatives/',
        openBlank: false
      },
      {
        label: 'Server Status',
        path: 'https://uptime.airgradient.net/status/servers',
        openBlank: true
      }
    ]
  },
  {
    group: 3,
    links: [
      {
        label: 'Privacy Policy',
        path: 'https://www.airgradient.com/privacy-policy/',
        openBlank: false
      },
      {
        label: 'Terms and Conditions',
        path: 'https://www.airgradient.com/terms-conditions/',
        openBlank: false
      },
      {
        label: 'Partner Program',
        path: 'https://www.airgradient.com/partner-program/',
        openBlank: false
      },
      {
        label: 'Resellers',
        path: 'https://www.airgradient.com/resellers/',
        openBlank: false
      },
      {
        label: 'Press / Influencers',
        path: 'https://www.airgradient.com/press/',
        openBlank: false
      }
    ]
  }
];
