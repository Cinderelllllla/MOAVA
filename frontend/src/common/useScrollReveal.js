/* 스크롤 등장 효과 (토스 소개 페이지 느낌)
   - 화면에 들어오는 요소를 아래에서 위로 스르륵 나타나게 한다. 같은 줄의 카드는 하나씩 차례로 나온다.
   - IntersectionObserver(요소가 화면에 보이는 순간을 알려 주는 브라우저 기능)를 쓴다.
   - 기능 코드는 건드리지 않고 class 만 붙였다 뗀다. 나타난 뒤에는 class 를 지워서 원래 hover 효과로 돌아간다.
   - 사용자가 "동작 줄이기"를 켰거나 브라우저가 기능을 지원하지 않으면 아무것도 하지 않는다(그냥 바로 보임). */
import { useLayoutEffect } from 'react';

export default function useScrollReveal(selector) {
  useLayoutEffect(() => {
    if (typeof window === 'undefined' || !('IntersectionObserver' in window)) return undefined;
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return undefined;

    const els = Array.from(document.querySelectorAll(selector));
    if (!els.length) return undefined;

    // 같은 부모 안에서 몇 번째인지에 따라 조금씩 늦게 나오게 (최대 0.4초)
    const order = new Map();
    els.forEach(el => {
      const n = order.get(el.parentElement) || 0;
      order.set(el.parentElement, n + 1);
      el.style.setProperty('--reveal-delay', `${Math.min(n * 0.09, 0.4)}s`);
      el.classList.add('reveal');
    });

    const done = e => {
      if (e.target !== e.currentTarget || e.propertyName !== 'transform') return;
      const el = e.currentTarget;
      el.classList.remove('reveal', 'is-visible');
      el.style.removeProperty('--reveal-delay');
      el.removeEventListener('transitionend', done);
    };

    const io = new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) return;
        entry.target.addEventListener('transitionend', done);
        entry.target.classList.add('is-visible');
        io.unobserve(entry.target);
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -8% 0px' });

    els.forEach(el => io.observe(el));
    return () => {
      io.disconnect();
      els.forEach(el => { el.classList.remove('reveal', 'is-visible'); el.style.removeProperty('--reveal-delay'); el.removeEventListener('transitionend', done); });
    };
  }, [selector]);
}
