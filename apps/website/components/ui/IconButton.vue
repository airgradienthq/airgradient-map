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
      width="16"
      height="16"
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
    width: 40px;
    height: 40px;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
    opacity: 1 !important;
  }

  .custom-icon-button:hover {
    transition: all var(--main-transition);
    color: var(--main-text-color);
    opacity: 1 !important;
  }

  .custom-icon-button.light {
    background-color: var(--main-white-color);
    border: 2px solid var(--grayColor400);
    color: var(--main-text-color);
  }

  .custom-icon-button.light:hover {
    background-color: var(--grayColor200);
  }

  .custom-icon-button.dark {
    background-color: var(--grayColor200);
  }

  .custom-icon-button :deep(.v-icon) {
    font-size: var(--font-size-base) !important;
  }

  .custom-icon {
    width: var(--font-size-base);
    height: var(--font-size-base);
    transition: all var(--main-transition);
    object-fit: contain;
    opacity: 1;
  }
</style>
