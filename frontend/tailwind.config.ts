import type { Config } from "tailwindcss";

/**
 * カラートークンは ui-design/面接練習チャットUI.dc.html の oklch 値をそのまま採用している。
 * - warm グレー系 (hue 60): テキスト・面・境界線
 * - 紫アクセント (hue 295): ボタン・アクティブ状態・アドバイス
 */
export default {
  content: [
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./hooks/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: [
          "var(--font-work-sans)",
          "system-ui",
          "-apple-system",
          "Segoe UI",
          "Roboto",
          "sans-serif",
        ],
      },
      colors: {
        background: "#ffffff",
        foreground: "oklch(0.22 0.008 60)",
        ink: {
          DEFAULT: "oklch(0.22 0.008 60)",
          soft: "oklch(0.5 0.008 60)",
          faint: "oklch(0.65 0.006 60)",
        },
        line: "oklch(0.9 0.005 60)",
        panel: {
          DEFAULT: "#ffffff",
          sidebar: "oklch(0.975 0.004 60)",
          bubble: "oklch(0.965 0.005 60)",
          hover: "oklch(0.955 0.006 60)",
          muted: "oklch(0.9 0.005 60)",
        },
        accent: {
          DEFAULT: "oklch(0.5 0.13 295)",
          strong: "oklch(0.4 0.12 295)",
          ink: "oklch(0.42 0.12 295)",
          surface: "oklch(0.94 0.025 295)",
          bubble: "oklch(0.96 0.025 295)",
          border: "oklch(0.78 0.09 295)",
          ring: "oklch(0.4 0.12 295 / 0.3)",
        },
      },
      keyframes: {
        waveBar: {
          "0%, 100%": { transform: "scaleY(0.25)" },
          "50%": { transform: "scaleY(1)" },
        },
        blinkDot: {
          "0%, 100%": { opacity: "1" },
          "50%": { opacity: "0.25" },
        },
        bounceDot: {
          "0%, 80%, 100%": { transform: "translateY(0)", opacity: "0.4" },
          "40%": { transform: "translateY(-5px)", opacity: "1" },
        },
        eqBar: {
          "0%, 100%": { transform: "scaleY(0.3)" },
          "50%": { transform: "scaleY(1)" },
        },
        pulseRing: {
          "0%": { transform: "scale(1)", opacity: "0.5" },
          "100%": { transform: "scale(1.55)", opacity: "0" },
        },
      },
      animation: {
        "wave-bar": "waveBar 0.7s ease-in-out infinite",
        "blink-dot": "blinkDot 1.1s ease-in-out infinite",
        "bounce-dot": "bounceDot 1.2s infinite",
        "eq-bar": "eqBar 0.7s ease-in-out infinite",
        "pulse-ring": "pulseRing 1.6s ease-out infinite",
      },
    },
  },
  plugins: [],
} satisfies Config;
