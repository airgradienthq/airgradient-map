import type { HeaderLink } from '~/types';

export const HEADER_LINKS_CONFIG_EN: HeaderLink[] = [
  {
    label: 'indoor-monitor',
    path: 'https://www.airgradient.com/indoor/',
    openBlank: false
  },
  {
    label: 'outdoor-monitor',
    path: '',
    openBlank: false,
    children: [
      {
        label: 'open-air',
        path: 'https://www.airgradient.com/outdoor/',
        openBlank: false
      },
      {
        label: 'open-air-max',
        path: 'https://www.airgradient.com/open-air-max/',
        openBlank: false
      }
    ]
  },
  {
    label: 'documentation',
    path: '',
    openBlank: false,
    children: [
      {
        label: 'build-instructions',
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
        label: 'integrations',
        path: 'https://www.airgradient.com/integrations/',
        openBlank: false
      },
      {
        label: 'undp-toolkit',
        path: 'https://www.airgradient.com/undp-air-quality-monitoring-toolkit/',
        openBlank: false
      }
    ]
  },
  {
    label: 'perspectives',
    path: '',
    openBlank: false,
    children: [
      {
        label: 'blog',
        path: 'https://www.airgradient.com/blog/',
        openBlank: false
      },
      {
        label: 'research',
        path: 'https://www.airgradient.com/research/',
        openBlank: false
      },
      {
        label: 'aq-forum',
        path: 'https://www.airgradient.com/forum/',
        openBlank: false
      },
      {
        label: 'open-source-initiative',
        path: 'https://www.airgradient.com/open-source-initiative/',
        openBlank: false
      },
      {
        label: 'about-us',
        path: 'https://www.airgradient.com/join-us/',
        openBlank: false
      }
    ]
  },
  {
    label: 'support',
    path: '',
    openBlank: false,
    children: [
      {
        label: 'support-forum',
        path: 'https://www.airgradient.com/support/',
        openBlank: false
      },
      {
        label: 'community-ngo',
        path: 'https://www.airgradient.com/community-ngo-program/',
        openBlank: false
      },
      {
        label: 'discussion-forum',
        path: 'https://forum.airgradient.com/',
        openBlank: false
      }
    ]
  },
  {
    label: 'map',
    path: '/',
    openBlank: false
  },
  {
    label: 'shop',
    path: 'https://www.airgradient.com/shop/',
    openBlank: false
  }
];

export const HEADER_LINKS_CONFIG_TH: HeaderLink[] = [
  {
    label: 'indoor-monitor',
    path: 'https://www.airgradient.com/indoor/',
    openBlank: false
  },
  {
    label: 'outdoor-monitor',
    path: '',
    openBlank: false,
    children: [
      {
        label: 'open-air',
        path: 'https://www.airgradient.com/outdoor/',
        openBlank: false
      },
      {
        label: 'open-air-max',
        path: 'https://www.airgradient.com/open-air-max/',
        openBlank: false
      }
    ]
  },
  {
    label: 'documentation',
    path: '',
    openBlank: false
  },
  {
    label: 'perspectives',
    path: '',
    openBlank: false,
    children: [
      {
        label: 'research',
        path: 'https://www.airgradient.com/research/',
        openBlank: false
      },
      {
        label: 'about-us',
        path: 'https://www.airgradient.com/join-us/',
        openBlank: false
      }
    ]
  },
  {
    label: 'support',
    path: 'https://www.airgradient.com/support/',
    openBlank: false
  },
  {
    label: 'map',
    path: '/',
    openBlank: false
  },
  {
    label: 'shop',
    path: 'https://www.airgradient.com/shop/',
    openBlank: false
  }
];
