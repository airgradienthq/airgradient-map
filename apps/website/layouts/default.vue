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

  const headless = ref(false);

  onMounted(() => {
    if (process.client) {
      if (window.location.href.includes('headless=true')) {
        headless.value = true;
        document.documentElement.classList.add('headless');
      }
    }
  });
</script>
