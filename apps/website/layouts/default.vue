<template>
  <!-- Internal company Header component - commented out for open source version -->
  <Header v-if="!headless" />

  <div class="main-content" :class="{ headless: headless }">
    <slot />
  </div>

  <!-- Internal company Footer component - commented out for open source version -->
  <Footer v-if="!headless" />
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue';
  import { useI18n } from '#imports';

  const { locale } = useI18n();

  const headless = ref(false);

  onMounted(() => {
    if (process.client) {
      if (window.location.href.includes('headless=true')) {
        headless.value = true;
      }

      if (locale.value === 'th') {
        document.body.classList.add('th-layout');
      } else {
        document.body.classList.remove('th-layout');
      }
    }
  });
</script>
