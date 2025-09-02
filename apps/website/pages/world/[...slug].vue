<template>
  <div>
    <!-- Temporarily hidden -->
    <!-- <h1>World: {{ slug.join(' / ') }}</h1>
    <div v-if="boundary">
      <ul class="list-unstyled d-flex gap-2">
        <li v-for="sub in boundary.subLevels" :key="sub">
          <UiButton :color="ButtonColor.SECONDARY">
            <NuxtLink :to="`/world/${path}/${[sub].join('/')}`">{{ sub }}</NuxtLink>
          </UiButton>
        </li>
      </ul>
    </div> -->
  </div>
</template>

<script setup lang="ts">
  // Temporarily hidden
  // import { useRoute } from 'vue-router';
  // import { useFetch, useHead } from 'nuxt/app';
  // import { ButtonColor } from '~/types';
  // import { useApiUrl } from '~/composables/shared/useApiUrl';

  // const { apiUrl } = useApiUrl();

  // const route = useRoute();
  // const slug = (route.params.slug as string[]) || [];

  // const path = slug.join('/');

  // const { data: boundary } = await useFetch<any>(`${apiUrl}/admin-boundaries/${path}`, {
  //   key: 'boundaries-world',
  //   default: () => null
  // });

  // useHead({
  //   title: slug.length
  //     ? `${slug[slug.length - 1]} Air Quality | AirGradient`
  //     : 'AirGradient Global Air Quality Map',
  //   meta: [
  //     {
  //       name: 'description',
  //       content: slug.length
  //         ? `Check real-time air quality in ${slug[slug.length - 1]} and surrounding areas.`
  //         : 'Check real-time air quality data around the world using our interactive map.'
  //     }
  //   ]
  // });
</script>
