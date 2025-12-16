<template>
  <Transition name="layout" mode="out-in">
    <div>
      <NuxtLayout>
        <NuxtPage />
      </NuxtLayout>
      <ClientOnly>
        <UiGlobalToast />
      </ClientOnly>
    </div>
  </Transition>
</template>

<script lang="ts" setup>
  import { useHead } from 'nuxt/app';

  useHead({
    title: 'AirGradient Map'
  });
</script>
