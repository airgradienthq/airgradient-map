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
  import { getPlatform, isNativeApp } from '~/utils';
  import { useHead } from 'nuxt/app';

  useHead({
    title: 'AirGradient Map'
  });

  if (process.client) {
    if (isNativeApp()) {
      console.log('App is running in a native environment (Capacitor)');
      const platform = getPlatform();
      document.body.classList.add('native-app');

      console.log('Platform:', platform);
      if (platform === 'ios') {
        document.body.classList.add('ios');
      } else {
        document.body.classList.add('android');
      }
    } else {
      console.log('App is running in a web environment');
      document.body.classList.add('web-app');
    }
  }
</script>