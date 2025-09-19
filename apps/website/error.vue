<template>
  <NuxtLayout>
    <div class="error-wrapper container-fluid bg-white">
      <div class="row justify-center py-5">
        <div v-if="error?.statusCode === 404" class="col-md-6 text-center mb-5 mb-md-0">
          <img src="./assets/images/404.jpg" class="img-fluid" alt="404 Error" />
        </div>
        <div class="col-md-6 align-self-center text-center text-md-start">
          <h1 class="mb-4">{{ errorTitle }}</h1>
          <p class="mb-4">{{ errorBody }}</p>
          <NuxtLink to="/" class="btn-large button-blue">{{ errorButton }}</NuxtLink>
        </div>
      </div>
    </div>
  </NuxtLayout>
</template>

<script setup lang="ts">
  import type { NuxtError } from 'nuxt/app';
  import { useI18n } from '#imports';
  import { computed } from 'vue';

  const props = defineProps({
    error: Object as () => NuxtError
  });

  const { t } = useI18n();

  const errorTitle = computed(() => t('error_title'));
  const errorBody = computed(() => t('error_body'));
  const errorButton = computed(() => t('error_button'));

  if (props.error?.statusCode === 404) {
    document.title = '404 Page not found';
  } else {
    document.title = 'Something went wrong';
  }
</script>
