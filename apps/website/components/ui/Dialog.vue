<template>
  <v-dialog
    :model-value="props.dialog.isOpen"
    :persistent="persistent"
    :max-width="DIALOG_SIZE_TO_WIDTH[size]"
    @update:model-value="close"
  >
    <v-card>
      <div v-if="showCloseIconButton" class="close-icon-button-box">
        <UiIconButton
          :ripple="false"
          :size="ButtonSize.NORMAL"
          icon="mdi-close"
          variant="plain"
          :legacy="true"
          @click="close"
        >
        </UiIconButton>
      </div>
      <div v-if="$slots.header">
        <slot name="header" />
      </div>
      <v-card-title v-else>
        <h4 class="text-center p-2 mb-0">{{ title }}</h4>
      </v-card-title>
      <v-card-text>
        <slot name="body" />
      </v-card-text>
      <v-card-actions v-if="!hideFooter">
        <div v-if="$slots.footer">
          <slot name="footer" />
        </div>
        <div v-else>
          <v-spacer />
          <UiButton @click="close">{{ closeLabel }}</UiButton>
        </div>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
  import { DIALOG_SIZE_TO_WIDTH } from '~/constants/shared/ui';
  import { dialogStore } from '~/composables/shared/ui/useDialog';
  import { DialogInstance } from '~/types';
  import { ButtonSize, DialogSize } from '~/types';

  const props = defineProps({
    /**
     * The dialog instance object to control this dialog.
     * @type {DialogInstance}
     * @required
     */
    dialog: {
      type: Object as () => DialogInstance,
      required: true
    },

    /**
     * Dialog title text.
     * @type {string}
     * @default ''
     */
    title: {
      type: String,
      default: ''
    },

    /**
     * Text for the close button.
     * @type {string}
     * @default 'Ok'
     */
    closeLabel: {
      type: String,
      default: 'Ok'
    },

    /**
     * Whether the dialog can be closed by clicking outside.
     * @type {boolean}
     * @default false
     */
    persistent: {
      type: Boolean,
      default: false
    },

    /**
     * Size of the dialog.
     * @type {DialogSize}
     * @default DialogSize.M
     */
    size: {
      type: String as () => DialogSize,
      default: DialogSize.M
    },

    /**
     * Whether to hide the footer actions.
     * @type {boolean}
     * @default false
     */
    hideFooter: {
      type: Boolean,
      default: false
    },

    /**
     * Whether to show the close icon button.
     * @type {boolean}
     * @default true
     */
    showCloseIconButton: {
      type: Boolean,
      default: true
    },

    /**
     * Whether to use a custom close handler.
     * If true, emits a 'close' event instead of closing via the dialog store.
     * @type {boolean}
     * @default false
     */
    customCloseHandler: {
      type: Boolean,
      default: false
    }
  });

  const emit = defineEmits<{
    'update:isOpen': [value: boolean];
    close: [];
  }>();

  function close() {
    emit('update:isOpen', false);

    if (props.customCloseHandler) {
      emit('close');
    } else {
      dialogStore.close(props.dialog?.dialogId);
    }
  }
</script>

<style scoped>
  .close-icon-button-box {
    display: flex;
    justify-content: flex-end;
    position: absolute;
    top: 8px;
    right: 8px;
  }
</style>
