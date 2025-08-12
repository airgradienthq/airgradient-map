//@ts-ignore
export default defineNuxtConfig({
  ssr: !process.env.NUXT_PUBLIC_MOBILE,
  runtimeConfig: {
    public: {
      apiUrl:
        process.env.NODE_ENV === 'production'
          ? 'https://map-data-int.airgradient.com/map/api/v1'
          : 'http://localhost:3001/map/api/v1'
    }
  },
  css: [
    'vuetify/lib/styles/main.sass',
    '@mdi/font/css/materialdesignicons.min.css',
    '@/assets/styles/bootstrap.min.css',
    '@/assets/styles/main.scss',
    'leaflet-geosearch/dist/geosearch.css'
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
    '@nuxtjs/leaflet'
  ],
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
