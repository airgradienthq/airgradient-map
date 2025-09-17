# Build stage
FROM node:22-alpine AS builder

WORKDIR /app

<<<<<<< HEAD
# Install Java for grib2json conversion (required for real GFS data)
RUN apt-get update && \
    apt-get install -y default-jre-headless && \
    rm -rf /var/lib/apt/lists/*

# Install grib2json tool
RUN npm install -g weacast-grib2json

# Install deps and copy project to container
=======
# Copy package files
>>>>>>> development
COPY package*.json ./
COPY tsconfig*.json ./

# Install dependencies
RUN npm install -g @nestjs/cli
RUN npm ci
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

# Copy built application and node_modules from builder stage
COPY --from=builder --chown=nestjs:nodejs /app/dist ./dist
COPY --from=builder --chown=nestjs:nodejs /app/node_modules ./node_modules
COPY --from=builder --chown=nestjs:nodejs /app/package*.json ./
COPY --from=builder --chown=nestjs:nodejs /app/tsconfig*.json ./

# Switch to non-root user
USER nestjs

# Expose port
EXPOSE 3001

# Use dumb-init for proper signal handling
ENTRYPOINT ["dumb-init", "--"]

# Start the application
CMD ["node", "dist/main"]