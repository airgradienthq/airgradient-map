<template>
  <ClientOnly>
    <Map />
  </ClientOnly>

  <div class="p-4">
    <h4>Browse Cities</h4>
    <ul class="list-unstyled d-flex gap-2">
      <li v-for="sub in boundary?.subLevels" :key="sub">
        <UiButton :color="ButtonColor.SECONDARY">
          <NuxtLink :to="`/world/${[sub].join('/')}`">{{ sub }}</NuxtLink>
        </UiButton>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
  import { useHead } from 'nuxt/app';
  import { useFetch } from 'nuxt/app';
  import { useApiUrl } from '~/composables/shared/useApiUrl';
  import { ButtonColor } from '~/types';

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
  const { apiUrl } = useApiUrl();

  const { data: boundary } = await useFetch<any>(`${apiUrl}/admin-boundaries/`, {
    key: 'boundaries-index',
    default: () => null
  });
</script>
