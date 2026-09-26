/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        nexus: {
          900: '#0B0F19',
          800: '#111827',
          700: '#1F2937',
          600: '#374151',
          accent: '#6366F1',
          cyan: '#06B6D4',
          emerald: '#10B981',
          rose: '#F43F5E',
        }
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
      },
      backdropBlur: {
        xs: '2px',
      }
    },
  },
  plugins: [],
}
