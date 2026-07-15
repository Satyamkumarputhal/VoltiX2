/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // VoltiX Operational Dark Palette
        'grid-base':    '#0d1117',  // Near-black canvas
        'grid-surface': '#161b22',  // Card & panel backgrounds
        'grid-raised':  '#1c2128',  // Elevated surfaces
        'grid-border':  '#30363d',  // Subtle dividers
        'grid-muted':   '#8b949e',  // Secondary text
        // State colours — operational clarity
        'accent-green':  '#39d353', // Healthy / normal
        'accent-blue':   '#388bfd', // Info / links / active
        'accent-amber':  '#f0a500', // Warning / medium
        'accent-orange': '#e05c00', // High severity
        'accent-red':    '#cf222e', // Critical / breach
        // Severity aliases
        severity: {
          low:      '#8b949e',
          medium:   '#f0a500',
          high:     '#e05c00',
          critical: '#cf222e',
        },
      },
      fontFamily: {
        mono: ['"JetBrains Mono"', '"Fira Code"', 'monospace'],
        sans: ['"Inter"', 'system-ui', 'sans-serif'],
      },
      animation: {
        'pulse-critical': 'pulse 1s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'slide-in': 'slideIn 0.2s ease-out',
        'fade-in': 'fadeIn 0.3s ease-out',
      },
      keyframes: {
        slideIn: {
          '0%': { transform: 'translateY(-4px)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
      },
    },
  },
  plugins: [],
};
