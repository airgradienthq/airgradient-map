//@ts-ignore
export default defineNuxtConfig({
  runtimeConfig: {
    public: {
      apiUrl:
        process.env.APP_ENV === 'production'
          ? 'https://map-data.airgradient.com/map/api/v1'
          : process.env.APP_ENV === 'staging'
            ? 'https://map-data-int.airgradient.com/map/api/v1'
            : 'http://localhost:3001/map/api/v1',
      trustedContext: process.env.NUXT_PUBLIC_TRUSTED_CONTEXT || ''
    }
  },
  css: [
    'vuetify/lib/styles/main.sass',
    '@mdi/font/css/materialdesignicons.min.css',
    '@/assets/styles/bootstrap.min.css',
    '@/assets/styles/main.scss',
    'leaflet-geosearch/dist/geosearch.css',
    'leaflet-velocity/dist/leaflet-velocity.css'
  ],
  build: {
    transpile: ['vuetify']
  },

  vite: {
    define: {
      'process.env.DEBUG': false
    },
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: `
          @use "@/assets/styles/_variables.scss" as *;
          @use "@/assets/styles/mixins.scss" as *;
          `
        }
      }
    }
  },

  modules: [
    [
      '@pinia/nuxt',
      {
        autoImports: ['defineStore', ['defineStore', 'definePiniaStore']]
      }
    ],
    '@nuxtjs/leaflet',
    '@nuxtjs/i18n'
  ],
  i18n: {
    detectBrowserLanguage: {
      useCookie: true,
      alwaysRedirect: true
    },
    strategy: 'prefix_except_default',
    defaultLocale: 'en',
    locales: [
      { code: 'en', name: 'English', file: 'en.json' },
      { code: 'th', name: 'ไทย', file: 'th.json' }
      //  temporarily disabled
      // { code: 'es', name: 'Español', file: 'es.json' }
    ],
    lazy: true,
    langDir: 'locales/',
    fallbackLocale: 'en'
  },
  nitro: {
    output: {
      dir: process.env.NODE_ENV === 'development' ? '.output-dev' : '.output',
      serverDir: process.env.NODE_ENV === 'development' ? '.output-dev/server' : '.output/server',
      publicDir: process.env.NODE_ENV === 'development' ? '.output-dev/public' : '.output/public'
    }
  },

  typescript: {
    strict: true,
    typeCheck: true
  },

  compatibilityDate: '2025-01-08'
});
