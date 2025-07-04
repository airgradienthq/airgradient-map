import { defineNuxtPlugin } from 'nuxt/app';
import slideReveal from '~/utils/slideReveal';
 
export default defineNuxtPlugin(nuxtApp => {
  nuxtApp.vueApp.directive('slide-reveal', slideReveal);
}); 