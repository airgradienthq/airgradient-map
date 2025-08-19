<template>
  <div class="dropdown">
    <button class="dropdown-toggle btn-small button-white" @click="toggleLanguageDropdown">
      <img :src="`images/icons/${locale}.svg`" class="flag-icon" />
      {{ locales.find(l => l.code === locale)?.name }}
    </button>
    <div class="dropdown-menu lang-dropdown-menu" :class="{ show: activeLanguageDropdown }">
      <button v-for="locale in locales" class="dropdown-item btn-small button-white text-left">
        <NuxtLink
          :key="locale.code"
          :to="switchLocalePath(locale.code)"
          @click="
            () => {
              $i18n.setLocale(locale.code);
            }
          "
        >
          <img :src="`images/icons/${locale.code}.svg`" class="flag-icon" />
          {{ locale.name }}
        </NuxtLink>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useNuxtApp, useI18n, useSwitchLocalePath } from '#imports';
  import { ref } from 'vue';

  const { $i18n } = useNuxtApp();
  const { locale } = useI18n();
  const switchLocalePath = useSwitchLocalePath();
  const locales = $i18n.locales;
  const activeLanguageDropdown = ref(false);

  const toggleLanguageDropdown = () => {
    activeLanguageDropdown.value = !activeLanguageDropdown.value;
  };
</script>

<style lang="css">
  .flag-icon {
    height: 16px;
    width: auto;
  }

  .lang-dropdown-menu {
    max-height: 175px;
    overflow-y: auto;
  }
</style>
