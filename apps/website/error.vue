<script setup lang="ts">
  import type { NuxtError } from 'nuxt/app';

  const props = defineProps({
    error: Object as () => NuxtError
  });

  if (props.error?.statusCode === 404) {
    document.title = '404 Page not found';
  } else {
    document.title = 'Something went wrong';
  }
</script>

<template>
  <NuxtLayout>
    <div class="error-wrapper container-fluid bg-white">
      <div class="row justify-center py-5">
        <div v-if="error?.statusCode === 404" class="col-md-6 text-center mb-5 mb-md-0">
          <img src="./assets/images/404.jpg" class="img-fluid" alt="404 Error" />
        </div>
        <div class="col-md-6 align-self-center text-center text-md-start">
          <h1 class="mb-4">{{ $t('error-title') }}</h1>
          <p class="mb-4">{{ $t('error-body') }}</p>
          <NuxtLink to="/" class="btn-large button-blue">{{ $t('error-button') }}</NuxtLink>
        </div>
      </div>
    </div>
  </NuxtLayout>
</template>
