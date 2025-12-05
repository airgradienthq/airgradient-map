export const LICENSE_MAP = {
  'CC BY 4.0': {
    label: 'CC BY 4.0',
    url: 'https://creativecommons.org/licenses/by/4.0/'
  },
  'CC BY-SA 4.0': {
    label: 'CC BY-SA 4.0',
    url: 'https://creativecommons.org/licenses/by-sa/4.0/'
  },
  'ODC-BY': {
    label: 'ODC-BY',
    url: 'https://opendatacommons.org/licenses/by/'
  },
  'DbCL v1.0': {
    label: 'DbCL v1.0',
    url: 'https://opendatacommons.org/licenses/dbcl/1-0/'
  },
  'US Public Domain': {
    label: 'US Public Domain',
    url: 'http://www.usa.gov/publicdomain/label/1.0/' // from https://explore.openaq.org/locations/494923
  },
  'Mexico SINAICA Terms and Conditions': {
    label: 'Mexico SINAICA Terms and Conditions',
    url: 'https://datos.gob.mx/libreusomx' // from https://explore.openaq.org/locations/3309328
  },
  '政府標準利用規約（第2.0版） (Government Standard Terms of Use v2.0)': {
    label: '政府標準利用規約（第2.0版） (Government Standard Terms of Use v2.0)',
    url: 'https://www.env.go.jp/kanbo/koho/opendata.html' // from https://explore.openaq.org/locations/1214995
  }
} as const;

export const DATA_SOURCE_MAP = {
  AirGradient: {
    label: 'AirGradient',
    url: 'https://www.airgradient.com/'
  },
  OpenAQ: {
    label: 'OpenAQ',
    url: 'https://openaq.org/'
  },
  DustBoy: {
    label: 'DustBoy',
    url: 'https://www.cmuccdc.org'
  }
} as const;
