FROM node:20-alpine
WORKDIR /app
COPY package.json ./
COPY src/ ./src/
EXPOSE 3008
CMD ["node", "src/index.js"]
