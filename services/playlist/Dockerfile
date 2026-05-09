FROM node:20-alpine
WORKDIR /app
COPY package.json ./
COPY src/ ./src/
EXPOSE 3004
CMD ["node", "src/index.js"]
