# Build stage
FROM node:22-alpine AS builder

WORKDIR /app

# Copy package files
COPY package*.json ./
COPY tsconfig*.json ./

# Install dependencies
RUN npm ci
RUN npm install -g @nestjs/cli
RUN npm cache clean --force

# Copy source code
COPY src/ ./src/

# Build the application
RUN npm run build

# Production stage
FROM node:22-alpine AS production

# Create non-root user
RUN addgroup -g 1001 -S nodejs
RUN adduser -S nestjs -u 1001

WORKDIR /app

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init

# Install pm2 to run in cluster
RUN npm install -g pm2

# Copy built application and node_modules from builder stage
COPY --from=builder --chown=nestjs:nodejs /app/dist ./dist
COPY --from=builder --chown=nestjs:nodejs /app/node_modules ./node_modules
COPY --from=builder --chown=nestjs:nodejs /app/package*.json ./
COPY --from=builder --chown=nestjs:nodejs /app/tsconfig*.json ./
COPY --chown=nestjs:nodejs data-source data-source
COPY --chown=nestjs:nodejs database/migrations database/migrations
COPY --chown=nestjs:nodejs knexfile.ts knexfile.ts

# Switch to non-root user
USER nestjs

# Expose port
EXPOSE 3001

# Use dumb-init for proper signal handling
ENTRYPOINT ["dumb-init", "--"]

# Start default command to run the application
CMD ["node", "dist/api-main"]
