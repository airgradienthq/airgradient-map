import { ref, onMounted, onUnmounted } from 'vue';

interface HoverTextRevealOptions {
  revealDelay?: number;
  resetDelay?: number;
  transitionDuration?: number;
  translateX?: number;
}

export function useHoverTextReveal(options: HoverTextRevealOptions = {}) {
  const {
    revealDelay = 100,
    resetDelay = 2000,
    transitionDuration = 300,
    translateX = -60
  } = options;

  const elementRef = ref<HTMLElement | null>(null);
  let revealTimeout: ReturnType<typeof setTimeout> | null = null;
  let resetTimeout: ReturnType<typeof setTimeout> | null = null;
  let isHovering = false;
  let hasRevealed = false;
  let originalTransform = '';

  const ensureTextWrapped = (el: HTMLElement): HTMLElement => {
    let span = el.querySelector('span');
    if (!span) {
      span = document.createElement('span');
      while (el.firstChild) {
        span.appendChild(el.firstChild);
      }
      el.appendChild(span);
    }
    return span as HTMLElement;
  };

  const startReveal = () => {
    if (!elementRef.value || !isHovering || hasRevealed) return;
    
    const el = elementRef.value;
    const textEl = ensureTextWrapped(el);
    const containerWidth = el.offsetWidth;
    const textWidth = textEl.scrollWidth;
    
    if (textWidth > containerWidth) {
      textEl.style.transform = `translateX(${translateX}px)`;
      hasRevealed = true;
      
      if (isHovering) {
        resetTimeout = setTimeout(() => {
          if (isHovering) {
            textEl.style.transform = 'translateX(0)';
            hasRevealed = false;
          }
        }, resetDelay);
      }
    }
  };

  const resetText = () => {
    if (!elementRef.value) return;
    
    if (resetTimeout) {
      clearTimeout(resetTimeout);
      resetTimeout = null;
    }
    
    const textEl = elementRef.value.querySelector('span');
    if (textEl) {
      textEl.style.transform = 'translateX(0)';
    }
    hasRevealed = false;
  };

  const handleMouseEnter = () => {
    isHovering = true;
    
    if (resetTimeout) {
      clearTimeout(resetTimeout);
      resetTimeout = null;
    }
    
    revealTimeout = setTimeout(() => {
      if (isHovering) {
        startReveal();
      }
    }, revealDelay);
  };

  const handleMouseLeave = () => {
    isHovering = false;
    
    if (revealTimeout) {
      clearTimeout(revealTimeout);
      revealTimeout = null;
    }
    if (resetTimeout) {
      clearTimeout(resetTimeout);
      resetTimeout = null;
    }
    
    resetText();
  };

  const setupElement = (el: HTMLElement) => {
    elementRef.value = el;
    
    const textEl = ensureTextWrapped(el);
    el.style.overflow = 'hidden';
    textEl.style.transition = `transform ${transitionDuration}ms ease-out`;
    textEl.style.willChange = 'transform';
    textEl.style.display = 'inline-block';
    textEl.style.whiteSpace = 'nowrap';
    
    el.addEventListener('mouseenter', handleMouseEnter);
    el.addEventListener('mouseleave', handleMouseLeave);
  };

  const cleanup = () => {
    if (elementRef.value) {
      elementRef.value.removeEventListener('mouseenter', handleMouseEnter);
      elementRef.value.removeEventListener('mouseleave', handleMouseLeave);
    }
    
    if (revealTimeout) {
      clearTimeout(revealTimeout);
    }
    if (resetTimeout) {
      clearTimeout(resetTimeout);
    }
  };

  onUnmounted(cleanup);

  return {
    elementRef,
    setupElement,
    cleanup
  };
} 