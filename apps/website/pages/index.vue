<template>
  <ClientOnly>
    <Map />
  </ClientOnly>
</template>

<script setup lang="ts">
  import { useHead } from 'nuxt/app';

  useHead({
    title: 'AirGradient Global Air Quality Map',
    meta: [
      {
        name: 'description',
        content: 'Check real-time air quality data around the world using our interactive map.'
      },
      {
        name: 'keywords',
        content: 'air quality, AQI, real-time air pollution, air monitoring, world air quality'
      }
    ],
    script: [
      {
        type: 'application/ld+json',
        children: JSON.stringify({
          '@context': 'https://schema.org',
          '@type': 'WebPage',
          name: 'AirGradient Global Air Quality Map',
          description: 'View live air quality data from various locations worldwide.',
          mainEntity: {
            '@type': 'Place',
            name: 'World'
          }
        })
      }
    ]
  });
</script>
