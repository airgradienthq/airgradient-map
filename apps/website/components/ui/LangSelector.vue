<template>
  <div class="dropdown">
    <button class="dropdown-toggle btn-small button-white" @click="toggleLanguageDropdown">
      <img :src="`images/icons/${locale}.svg`" class="flag-icon" />
      {{ locales.find(l => l.code === locale)?.name }}
    </button>
    <div class="dropdown-menu lang-dropdown-menu" :class="{ show: activeLanguageDropdown }">
      <button
        v-for="localeOption in locales"
        :key="localeOption.code"
        type="button"
        class="dropdown-item btn-small button-white text-left"
        @click="setLocale(localeOption.code)"
      >
        <img :src="`images/icons/${localeOption.code}.svg`" class="flag-icon" />
        {{ localeOption.name }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useNuxtApp, useI18n } from '#imports';
  import { ref } from 'vue';

  const { $i18n } = useNuxtApp();
  const { locale } = useI18n();
  const locales = $i18n.locales;
  const activeLanguageDropdown = ref(false);

  const toggleLanguageDropdown = () => {
    activeLanguageDropdown.value = !activeLanguageDropdown.value;
  };

  const setLocale = (localeCode: string) => {
    $i18n.setLocale(localeCode);
    if (localeCode === 'th') {
      document.body.classList.add('th-layout');
    } else {
      document.body.classList.remove('th-layout');
    }

    toggleLanguageDropdown();
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
