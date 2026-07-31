
# ---- Stage 1: Build ----
FROM node:22-alpine AS build

WORKDIR /app

# Installer les dépendances (couche mise en cache tant que les manifestes ne changent pas)
COPY package.json package-lock.json* ./
RUN npm ci

# Copier le reste du code et builder en production
COPY . .
RUN npm run build

# ---- Stage 2: Serve ----
FROM nginx:1.27-alpine AS runtime

# Config Nginx pour une SPA Angular (fallback vers index.html)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Les fichiers statiques buildés (application builder -> dist/<projet>/browser)
COPY --from=build /app/dist/bi-dashboard/browser /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
