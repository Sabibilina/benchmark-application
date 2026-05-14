import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#151515",
        paper: "#f8faf8",
        line: "#d6ddd6",
        brand: "#0f766e",
        accent: "#f59e0b"
      }
    }
  },
  plugins: []
} satisfies Config;
