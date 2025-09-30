# Build stage
FROM node:22-alpine AS builder

WORKDIR /app

# Copy package files
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

# Install runtime dependencies
RUN apk add --no-cache \
    openjdk11-jre \
    dumb-init

# Create symlink so grib2json script can find Java
RUN ln -sf /usr/bin/java /bin/java

# Create non-root user
RUN addgroup -g 1001 -S nodejs && \
    adduser -S nestjs -u 1001

WORKDIR /app

# Install grib2json globally as root switching to non-root user
RUN npm install -g weacast-grib2json && \
    ln -sf /usr/local/lib/node_modules/weacast-grib2json/bin/grib2json /usr/local/bin/grib2json && \
    ln -sf /usr/local/lib/node_modules/weacast-grib2json/bin/grib2json.jar /usr/local/bin/grib2json.jar && \
    chmod +x /usr/local/bin/grib2json && \
    chmod +x /usr/local/lib/node_modules/weacast-grib2json/bin/grib2json

# Copy built application from builder
COPY --from=builder --chown=nestjs:nodejs /app/dist ./dist
COPY --from=builder --chown=nestjs:nodejs /app/node_modules ./node_modules
COPY --from=builder --chown=nestjs:nodejs /app/package*.json ./
COPY --from=builder --chown=nestjs:nodejs /app/tsconfig*.json ./

# Create directories for wind data with proper permissions
RUN mkdir -p /app/public/data/wind /app/temp && \
    chown -R nestjs:nodejs /app/public /app/temp

# Switch to non-root user
USER nestjs

# Expose port
EXPOSE 3001

# Use dumb-init for proper signal handling
ENTRYPOINT ["dumb-init", "--"]

# Start the application
CMD ["node", "dist/main"]