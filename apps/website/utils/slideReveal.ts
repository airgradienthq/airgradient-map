import { DirectiveBinding } from 'vue';

function ensureTextWrapped(el: HTMLElement): HTMLElement {
  // If the first child is a span, use it. Otherwise, wrap the text in a span.
  let span = el.querySelector('span');
  if (!span) {
    // Wrap all text nodes in a span
    span = document.createElement('span');
    // Move all child nodes into the span
    while (el.firstChild) {
      span.appendChild(el.firstChild);
    }
    el.appendChild(span);
  }
  return span as HTMLElement;
}

// DISABLED: slideReveal directive now does nothing to prevent any shifting/slide animation.
const slideReveal = {
  mounted() {},
};

export default slideReveal; 