import type { HeaderLink } from '~/types';

export const HEADER_LINKS_CONFIG: HeaderLink[] = [
  {
    label: 'Indoor Monitor',
    path: 'https://www.airgradient.com/indoor/',
    openBlank: false
  },
  {
    label: 'Outdoor Monitor',
    path: '',
    openBlank: false,
    children: [
      {
        label: 'Open Air',
        path: 'https://www.airgradient.com/outdoor/',
        openBlank: false
      },
      {
        label: 'Open Air Max',
        path: 'https://www.airgradient.com/open-air-max/',
        openBlank: false
      }
    ]
  },
  {
    label: 'Documentation',
    path: '',
    openBlank: false,
    children: [
      {
        label: 'Build Instructions',
        path: 'https://www.airgradient.com/documentation/',
        openBlank: false
      },
      {
        label: 'Homey',
        path: 'https://www.airgradient.com/homey/',
        openBlank: false
      },
      {
        label: 'Home Assistant',
        path: 'https://www.airgradient.com/home-assistant/',
        openBlank: false
      },
      {
        label: 'Integrations',
        path: 'https://www.airgradient.com/integrations/',
        openBlank: false
      },
      {
        label: 'UNDP AQ Monitoring Toolkit',
        path: 'https://www.airgradient.com/undp-air-quality-monitoring-toolkit/',
        openBlank: false
      }
    ]
  },
  {
    label: 'Perspectives',
    path: '',
    openBlank: false,
    children: [
      {
        label: 'Blog',
        path: 'https://www.airgradient.com/blog/',
        openBlank: false
      },
      {
        label: 'Research',
        path: 'https://www.airgradient.com/research/',
        openBlank: false
      },
      {
        label: 'Air Quality Forum',
        path: 'https://www.airgradient.com/forum/',
        openBlank: false
      },
      {
        label: 'Open Source Initiative',
        path: 'https://www.airgradient.com/open-source-initiative/',
        openBlank: false
      },
      {
        label: 'About Us',
        path: 'https://www.airgradient.com/join-us/',
        openBlank: false
      }
    ]
  },
  {
    label: 'Support',
    path: '',
    openBlank: false,
    children: [
      {
        label: 'Support Forum',
        path: 'https://www.airgradient.com/support/',
        openBlank: false,
      },
      {
        label: 'Community & NGO Program',
        path: 'https://www.airgradient.com/community-ngo-program/',
        openBlank: false
      },
      {
        label: 'Discussion Forum',
        path: 'https://forum.airgradient.com/',
        openBlank: false
      }
    ]
  },
  {
    label: 'Map',
    path: '/',
    openBlank: false
  },
  {
    label: 'Shop',
    path: 'https://www.airgradient.com/shop/',
    openBlank: false
  }
];
