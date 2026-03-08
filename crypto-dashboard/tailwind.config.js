/** @type {import('tailwindcss').Config} */
module.exports = {
    content: ["./src/**/*.{js,jsx,ts,tsx}"],
    theme: {
        extend: {
            colors: {
                atech: {
                    bg:   '#121212',
                    card: '#1C1C1C',
                    green: {
                        DEFAULT: '#4A7856',
                        light:   '#5A9268',
                        dark:    '#3A6244',
                    },
                },
                slate: {
                    950: '#0a0a0a',
                },
            },
        },
    },
    plugins: [],
}