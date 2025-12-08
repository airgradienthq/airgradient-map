FROM node:22-alpine

# Install runtime dependencies
RUN apk add --no-cache \
    openjdk17-jre-headless \
    dumb-init

# Create symlink so grib2json can find Java
RUN ln -sf /usr/bin/java /bin/java

WORKDIR /app

# Install grib2json globally
RUN npm install -g weacast-grib2json && \
    ln -sf /usr/local/lib/node_modules/weacast-grib2json/bin/grib2json /usr/local/bin/grib2json && \
    chmod +x /usr/local/bin/grib2json

# Copy package files
COPY package*.json ./
COPY tsconfig.json ./

# Install dependencies
RUN npm ci || (echo "npm ci failed" && exit 1)

# Copy source code
COPY src/ ./src/

# Build TypeScript - show full output
RUN npm run build 2>&1 || (echo "TypeScript build failed. Listing files:" && ls -R src/ && cat tsconfig.json && exit 1)

# Create temp directory
RUN mkdir -p /app/temp && chmod 777 /app/temp

# Use dumb-init for proper signal handling
ENTRYPOINT ["dumb-init", "--"]

# Start the service
CMD ["node", "dist/main.js"]
