/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  // All theme tokens (colors, fonts, animations, keyframes) are defined in
  // src/index.css @theme block (Tailwind v4 CSS-based config). Do NOT
  // duplicate them here — the JS config is only retained for content paths.
  theme: {},
  plugins: [],
};
