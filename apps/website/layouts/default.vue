<template>
  <!-- Internal company Header component - commented out for open source version -->
  <Header v-if="!headless" />

  <div class="main-content">
    <slot />
  </div>

  <!-- Internal company Footer component - commented out for open source version -->
  <Footer v-if="!headless" />
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue';
  import { useI18n, useNuxtApp } from '#imports';

  const { locale } = useI18n();
  const { $i18n } = useNuxtApp();

  const headless = ref(false);

  onMounted(() => {
    if (process.client) {
      if (window.location.href.includes('headless=true')) {
        headless.value = true;
        document.documentElement.classList.add('headless');
      }

      if (window.location.href.includes('embedded=true')) {
        document.documentElement.classList.add('embedded');
      }

      if (window.location.href.includes('external_lang=en')) {
        $i18n.setLocale('en');
        document.body.classList.remove('th-layout');
      }

      if (locale.value === 'th') {
        document.body.classList.add('th-layout');
      } else {
        document.body.classList.remove('th-layout');
      }
    }
  });
</script>
