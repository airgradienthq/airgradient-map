import type { HeaderLink } from '~/types';

export const HEADER_LINKS_CONFIG_EN: HeaderLink[] = [
  {
    label: 'indoor_monitor',
    path: 'https://www.airgradient.com/indoor/',
    openBlank: false
  },
  {
    label: 'outdoor_monitor',
    path: '',
    openBlank: false,
    children: [
      {
        label: 'open_air',
        path: 'https://www.airgradient.com/outdoor/',
        openBlank: false
      },
      {
        label: 'open_air_max',
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
        label: 'build_instructions',
        path: 'https://www.airgradient.com/documentation/',
        openBlank: false
      },
      {
        label: 'homey',
        path: 'https://www.airgradient.com/homey/',
        openBlank: false
      },
      {
        label: 'home_assistant',
        path: 'https://www.airgradient.com/home-assistant/',
        openBlank: false
      },
      {
        label: 'integrations',
        path: 'https://www.airgradient.com/integrations/',
        openBlank: false
      },
      {
        label: 'undp_toolkit',
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
        label: 'aq_forum',
        path: 'https://www.airgradient.com/forum/',
        openBlank: false
      },
      {
        label: 'open_source_initiative',
        path: 'https://www.airgradient.com/open-source-initiative/',
        openBlank: false
      },
      {
        label: 'about_us',
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
        label: 'support_forum',
        path: 'https://www.airgradient.com/support/',
        openBlank: false
      },
      {
        label: 'community_ngo',
        path: 'https://www.airgradient.com/community-ngo-program/',
        openBlank: false
      },
      {
        label: 'discussion_forum',
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
    label: 'indoor_monitor',
    path: 'https://www.airgradient.com/th/indoor/',
    openBlank: false
  },
  {
    label: 'outdoor_monitor',
    path: '',
    openBlank: false,
    children: [
      {
        label: 'open_air',
        path: 'https://www.airgradient.com/th/outdoor/',
        openBlank: false
      },
      {
        label: 'open_air_max',
        path: 'https://www.airgradient.com/th/open-air-max/',
        openBlank: false
      }
    ]
  },
  {
    label: 'documentation',
    path: 'https://www.airgradient.com/th/documentation/',
    openBlank: false
  },
  {
    label: 'perspectives',
    path: '',
    openBlank: false,
    children: [
      {
        label: 'research',
        path: 'https://www.airgradient.com/th/research/',
        openBlank: false
      },
      {
        label: 'about_us',
        path: 'https://www.airgradient.com/th/join-us/',
        openBlank: false
      }
    ]
  },
  {
    label: 'support',
    path: 'https://www.airgradient.com/th/support/',
    openBlank: false
  },
  {
    label: 'map',
    path: '/',
    openBlank: false
  },
  {
    label: 'shop',
    path: 'https://www.airgradient.com/th/shop/',
    openBlank: false
  }
];
