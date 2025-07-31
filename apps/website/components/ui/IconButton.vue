<template>
  <v-btn
    v-if="icon && !customIcon"
    :ripple="ripple"
    :disabled="disabled"
    :icon="icon"
    variant="plain"
    :class="['custom-icon-button', style]"
    :size="size === ButtonSize.NORMAL ? 'default' : 'small'"
    @click="handleClick"
  >
  </v-btn>

  <v-btn
    v-else-if="customIcon"
    :ripple="ripple"
    :disabled="disabled"
    variant="plain"
    :class="['custom-icon-button', style]"
    :size="size === ButtonSize.NORMAL ? 'default' : 'small'"
    @click="handleClick"
  >
    <img
      width="24"
      height="24"
      :src="`/images/icons/${customIcon}`"
      :alt="iconAlt"
      class="custom-icon"
    />
  </v-btn>

  <template v-else>
    <slot></slot>
  </template>
</template>

<script setup lang="ts">
  import { PropType } from 'vue';
  import { ButtonSize } from '~/types';

  const props = defineProps({
    /**
     * Size of the icon button.
     * @type {ButtonSize}
     * @default ButtonSize.NORMAL
     */
    size: {
      type: String as PropType<ButtonSize>,
      default: ButtonSize.NORMAL
    },
    /**
     * Whether the button is disabled.
     * @type {boolean}
     * @default false
     */
    disabled: {
      type: Boolean,
      default: false
    },
    /**
     * Whether to show ripple effect on click.
     * @type {boolean}
     * @default false
     */
    ripple: {
      type: Boolean,
      default: false
    },
    /**
     * Icon name for v-btn (e.g. 'mdi-close').
     * @type {string}
     */
    icon: {
      type: String,
      default: ''
    },
    /**
     * Path to custom SVG icon. Please use the public/images/icons/ folder to store your icons.
     * @type {string}
     */
    customIcon: {
      type: String,
      default: ''
    },
    /**
     * Alt text for custom icon.
     * @type {string}
     */
    iconAlt: {
      type: String,
      default: 'Icon'
    },
    /**
     * Style of the icon button.
     * @type {'light' | 'dark'}
     * @default 'light'
     */
    style: {
      type: String as PropType<'light' | 'dark'>,
      default: 'light'
    }
  });

  const emit = defineEmits(['click']);
  /**
   * Handles the button click event.
   * Emits a click event if the button is not disabled.
   * @param {Event} event - The click event.
   */
  const handleClick = (event: Event) => {
    if (!props.disabled) {
      emit('click', event);
    }
  };
</script>

<style scoped>
  .custom-icon-button {
    transition: all var(--main-transition);
    border-radius: 50%;
    width: 34px;
    height: 34px;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
    opacity: 1 !important;
  }

  .custom-icon-button:hover {
    transition: all var(--main-transition);
    color: var(--main-text-color) !important;
    opacity: 1 !important;

    .custom-icon {
      filter: invert(42%) sepia(73%) saturate(323%) hue-rotate(171deg) brightness(113%)
        contrast(90%);
    }
  }

  .custom-icon-button.light {
    background-color: var(--main-white-color) !important;
    border: 2px solid var(--grayColor400) !important;
    color: #212121 !important;
    opacity: 1 !important;
  }

  .custom-icon-button.light:hover {
    background-color: var(--hover-bg-color) !important;
    color: #212121 !important;
    opacity: 1 !important;
  }

  .custom-icon-button.dark {
    background-color: #eeede4 !important;
    color: #212121 !important;
    opacity: 1 !important;
  }

  /* Target Vuetify button element specifically */
  .custom-icon-button.light.v-btn {
    background-color: var(--main-white-color) !important;
    border: 2px solid var(--grayColor400) !important;
    color: #212121 !important;
    opacity: 1 !important;
  }

  .custom-icon-button.light.v-btn:hover {
    background-color: var(--hover-bg-color) !important;
    color: #212121 !important;
    opacity: 1 !important;
  }

  .custom-icon-button.dark.v-btn {
    background-color: #eeede4 !important;
    color: #212121 !important;
    opacity: 1 !important;
  }

  .custom-icon {
    transition: all var(--main-transition);
    object-fit: contain;
    opacity: 1 !important;
  }
</style>
