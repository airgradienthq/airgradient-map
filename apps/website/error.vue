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
    <div class="container py-5" style="@media (min-width: 992px) {height: calc(100vh - 129px)}">
      <div class="row justify-center">
        <div v-if="error?.statusCode === 404" class="col-md-6 text-center mb-5 mb-md-0">
          <img src="./assets/images/404.jpg" class="img-fluid" alt="404 Error" />
        </div>
        <div class="col-md-6 align-self-center text-center text-md-start">
          <h1 class="mb-4">You ran out of air.</h1>
          <p class="mb-4">The page you're looking for is beyond our reach…</p>
          <NuxtLink to="/" class="btn-large button-blue"> Go to Homepage </NuxtLink>
        </div>
      </div>
    </div>
  </NuxtLayout>
</template>
