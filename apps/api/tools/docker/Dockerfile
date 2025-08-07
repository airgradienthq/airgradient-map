# TODO: Add multistage
FROM node:23
WORKDIR /app

# Install Java for grib2json conversion (required for real GFS data)
RUN apt-get update && \
    apt-get install -y default-jre-headless && \
    rm -rf /var/lib/apt/lists/*

# Install deps and copy project to container
COPY package*.json ./
RUN npm install
COPY . .

# First build and run with dev tag
## TODO: prod options to run
RUN npm run build