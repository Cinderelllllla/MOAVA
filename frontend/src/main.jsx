import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
// 스타일 순서 유지(원본 HTML cascade): base → themes → layout → pages → spec
import './styles/base.css';
import './styles/themes.css';
import './styles/layout.css';
import './styles/pages.css';
import './styles/spec.css';
import './styles/tailwind.css';   // Tailwind 유틸리티(보조) — 기존 CSS 뒤에 로드

ReactDOM.createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>);
