# Wind Data System - Architecture & Functionality

## Overview

The Wind Data System is a distributed architecture that fetches, processes, stores, and visualizes global wind forecast data from NOAA's Global Forecast System (GFS). The system consists of three main components working together:

1. **Wind Service** - Scheduled background service that downloads and processes GFS data
2. **API Service** - RESTful API that serves wind data to clients
3. **Website** - Frontend application that visualizes wind data on an interactive map

The system provides near-real-time global wind visualization at 1° resolution (approximately 111 km at the equator), updated every 3 hours from NOAA's GFS model.

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         NOAA GFS                                │
│              (Global Forecast System - 1° resolution)           │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     │ HTTPS Download (GRIB2)
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Wind Service                                 │
│  • Scheduled downloads (every 3 hours)                         │
│  • GRIB2 → JSON conversion                                      │
│  • Data validation & transformation                             │
│  • Batch insertion to PostgreSQL                                │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     │ PostgreSQL INSERT
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    PostgreSQL Database                          │
│                    (wind_data table)                            │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     │ SQL SELECT (latest forecast)
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API Service (NestJS)                          │
│  • GET /map/api/v1/wind-data/current                            │
│  • Bounding box queries                                          │
│  • Grid format transformation                                    │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     │ HTTP GET (JSON)
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Website (Nuxt.js)                            │
│  • Leaflet map integration                                       │
│  • Leaflet-velocity visualization                               │
│  • Real-time wind layer rendering                               │
└─────────────────────────────────────────────────────────────────┘
```

## Data Flow

1. **Data Acquisition**: Wind Service downloads GFS data from NOAA NOMADS
2. **Data Processing**: GRIB2 files converted to JSON, validated, and transformed
3. **Data Storage**: Processed data stored in PostgreSQL with spatial indexing
4. **Data Retrieval**: API queries database for latest forecast within bounding box
5. **Data Transformation**: API transforms records to optimized grid format
6. **Data Visualization**: Website fetches data, transforms to leaflet-velocity format, renders on map

## Technology Stack

- **Wind Service**: Node.js 22, TypeScript, node-cron, PostgreSQL client
- **API Service**: NestJS, TypeScript, PostgreSQL, Swagger
- **Website**: Nuxt.js 3, Vue 3, Leaflet, Leaflet-Velocity
- **Database**: PostgreSQL with spatial indexes
- **Infrastructure**: Docker, Docker Compose

---

## 1. Wind Service

### Purpose

The Wind Service is an isolated, scheduled background service responsible for:

- Downloading GFS wind forecast data from NOAA
- Converting GRIB2 binary files to JSON
- Validating and transforming data
- Storing processed data in PostgreSQL

### Architecture

**Service Components**:

- `WindService` (`main.ts`) - Main orchestrator and cron scheduler
- `WindDataProcessingService` - Coordinates the data pipeline
- `GFSDownloaderService` - Downloads GFS data from NOAA NOMADS
- `GribConverterService` - Converts GRIB2 to JSON using `grib2json`
- `WindDataRepositoryService` - Database operations and data transformation

### Scheduled Updates

**Cron Schedule**: Every 3 hours at 02:00, 05:00, 08:00, 11:00, 14:00, 17:00, 20:00, 23:00 UTC

**Smart Fetching Logic**:

- Before each scheduled run, checks if new data is available
- Compares forecast time in database with expected availability
- Only downloads if data is stale (more than 6 hours old)
- Accounts for 5-hour GFS data availability delay after model run

### Data Source

**NOAA GFS (Global Forecast System)**:

- **Resolution**: 1° (approximately 111 km at equator)
- **Data Type**: 10-meter wind components (U and V)
- **Format**: GRIB2 binary format
- **Source URL**: `nomads.ncep.noaa.gov/cgi-bin/filter_gfs_1p00.pl`

### Processing Pipeline

1. **Tool Validation**: Locates `grib2json` converter tool
2. **GFS Download**: Downloads GRIB2 file from NOAA
3. **GRIB2 Conversion**: Converts U and V components to JSON
4. **Freshness Check**: Validates downloaded data is newer than database
5. **Data Transformation**: Converts grid data to database records
6. **Batch Insertion**: Inserts records in batches of 10,000

### Data Transformation

**Input** (from grib2json):

- Grid format with header metadata (nx, ny, dx, dy, la1, lo1, refTime)
- Data arrays for U and V components

**Output** (database records):

- Individual records: `(longitude, latitude, forecast_time, u_component, v_component)`
- Grid points calculated: `latitude = la1 - row * dy`, `longitude = lo1 + col * dx`

### Database Schema

**Table**: `wind_data`

**Columns**:

- `longitude` (REAL) - Longitude coordinate
- `latitude` (REAL) - Latitude coordinate
- `forecast_time` (TIMESTAMP) - Forecast reference time
- `u_component` (REAL) - Eastward wind component (m/s)
- `v_component` (REAL) - Northward wind component (m/s)

**Constraints**:

- Primary key: `(longitude, latitude, forecast_time)`
- Unique constraint prevents duplicates

**Indexes**:

- `idx_wind_data_forecast_time_desc` - For finding latest forecast
- `idx_wind_data_time_location` - Composite index for queries
- `idx_wind_data_location` - For spatial queries

**Batch Insert Strategy**:

- Batch size: 10,000 records (safety margin below PostgreSQL's 65,535 parameter limit)
- Uses transactions (BEGIN/COMMIT/ROLLBACK)
- `ON CONFLICT DO UPDATE` for upsert behavior
- Historical data is retained (no deletion)

### Error Handling

- **Database Connection**: Validates on startup, exits if fails
- **Download Failures**: Tries multiple cycles and dates
- **Conversion Failures**: 60-second timeout, cleanup on error
- **Insertion Failures**: Transaction rollback, batch-level error handling
- **Validation Failures**: Returns empty array, prevents data corruption

---

## 2. API

### Purpose

The API Service provides RESTful endpoints for querying wind data stored in the database. It transforms raw database records into an optimized grid format suitable for map visualization.

### Architecture

**Module Structure**:

- `WindDataModule` - NestJS module for wind data functionality
- `WindDataController` - HTTP endpoints
- `WindDataService` - Business logic and grid transformation
- `WindDataRepository` - Database queries

### Endpoints

**GET `/map/api/v1/wind-data/current`**

**Query Parameters**:

- `xmin` (number) - Minimum longitude (western bound)
- `xmax` (number) - Maximum longitude (eastern bound)
- `ymin` (number) - Minimum latitude (southern bound)
- `ymax` (number) - Maximum latitude (northern bound)

**Response Format**:

```json
{
  "header": {
    "forecastTime": "2025-11-10T06:00:00.000Z",
    "bounds": {
      "north": 70,
      "south": 35,
      "west": -10,
      "east": 40
    },
    "resolution": {
      "dx": 1.0,
      "dy": 1.0
    },
    "grid": {
      "nx": 50,
      "ny": 35
    }
  },
  "data": {
    "u": [1.5, 2.3, 1.8, ...],
    "v": [-0.5, 1.2, 0.8, ...]
  }
}
```

### Data Retrieval

**Repository Logic**:

- Queries latest forecast data (`MAX(forecast_time)`)
- Adds 2° buffer around requested bounds for smooth visualization
- Handles antimeridian crossing (longitude wrap-around)
- Handles global view (viewport spans >360°)
- Normalizes longitude to 0-360° range for database queries
- Orders results: latitude DESC, longitude ASC (north-to-south, west-to-east)

**Query Optimization**:

- Uses spatial indexes for efficient bounding box queries
- Composite indexes for time-location queries
- Clamps latitude to valid range (-90 to 90)

### Data Transformation

**Service Logic** (`transformToGridFormat`):

1. Calculates actual bounds from data
2. Determines grid dimensions (nx, ny) from unique coordinates
3. Calculates resolution (dx, dy) from coordinate spacing
4. Creates coordinate-to-index mapping
5. Fills U and V arrays in grid order (index = y \* nx + x)
6. Replaces null values with 0 to maintain grid structure

**Grid Format**:

- Data ordered: north-to-south, west-to-east
- Index calculation: `index = y * nx + x`
- Regular grid with fixed dx/dy resolution
- Compatible with leaflet-velocity library

### Error Handling

- **No Data Found**: Returns `NotFoundException` (WIND_002)
- **Query Failures**: Returns `InternalServerErrorException` (WIND_003)
- **Validation**: Query parameters validated via class-validator
- **Logging**: Comprehensive logging at repository and service levels

### API Documentation

- Swagger/OpenAPI documentation available
- Endpoints tagged as "Wind Data"
- Request/response schemas documented
- Example queries provided

---

## 3. Website

### Purpose

The Website component provides an interactive map interface for visualizing wind data. Users can toggle a wind layer that displays animated wind vectors overlaid on the map.

### Wind Visualization Component

**Features**:

- Dynamic loading based on map bounds
- Debounced reload on zoom/pan (500ms delay)
- Loading state management
- Error handling and validation
- Proper cleanup on unmount

**Lifecycle**:

1. Component mounts → Loads leaflet-velocity library
2. Map ready + enabled → Fetches wind data for current bounds
3. Map move/zoom → Debounced reload of wind data
4. Enabled toggle → Shows/hides wind layer
5. Component unmount → Removes layer and event listeners

### Data Fetching

**API Integration**:

- Fetches from: `${apiUrl}/wind-data/current?xmin=${xmin}&xmax=${xmax}&ymin=${ymin}&ymax=${ymax}`
- 50-second timeout with AbortController
- Validates response structure before processing

**Data Flow**:

1. Get map bounds (west, east, south, north)
2. Fetch wind data from API
3. Transform API format to leaflet-velocity format
4. Validate transformed data structure
5. Create velocity layer with transformed data
6. Add layer to map

### Data Transformation

**Transformer** (`transformToLeafletVelocityFormat`):

Converts API response format to leaflet-velocity (grib2json-compatible) format:

**API Format**:

```typescript
{
  header: {
    forecastTime: string,
    bounds: { north, south, west, east },
    resolution: { dx, dy },
    grid: { nx, ny }
  },
  data: { u: number[], v: number[] }
}
```

**Leaflet-Velocity Format**:

```typescript
[
  {
    header: {
      refTime: string,
      la1: number,  // First latitude (northernmost)
      la2: number,  // Last latitude (southernmost)
      lo1: number,  // First longitude (westernmost)
      lo2: number,  // Last longitude (easternmost)
      nx: number,
      ny: number,
      dx: number,
      dy: number,
      parameterNumber: 2,  // U-component
      parameterNumberName: 'eastward_wind'
    },
    data: number[]  // U-component values
  },
  {
    header: { /* same but parameterNumber: 3, northward_wind */ },
    data: number[]  // V-component values
  }
]
```

### Visualization Configuration

**Velocity Layer Options**:

- `displayValues`: true - Shows wind speed/direction overlay
- `minVelocity`: 0 m/s
- `maxVelocity`: 15 m/s
- `velocityScale`: 0.015 - Controls arrow size
- `opacity`: 0.97
- `colorScale`: Custom turquoise-to-purple gradient (0-15 m/s)

**Color Scale**:

- Range: 0-15 m/s (0-54 km/h)
- Colors: Turquoise (calm) → Blue → Purple (strong winds)
- 13 color stops for smooth gradient

### Performance Optimizations

- **Debouncing**: 500ms delay on map move/zoom events
- **Conditional Loading**: Only loads when enabled
- **Bounds-based Fetching**: Only fetches data for visible area
- **Lazy Library Loading**: Dynamic import of leaflet-velocity
- **Cleanup**: Proper removal of layers and event listeners

---

## 4. Infrastructure

### Docker Compose Architecture

The system runs as a multi-container Docker Compose setup:

**Services**:

1. **postgrex** - PostgreSQL database with PostGIS
2. **db-migrate** - Database migration runner (runs automatically)
3. **mapapi** - NestJS API backend (PM2 with multiple instances)
4. **cron** - Background cron jobs service
5. **wind-service** - Wind data processing service
6. **app** - Nuxt.js frontend website

### Wind Service Container

**Build Process**:

1. Install system dependencies
2. Install grib2json globally
3. Copy package files and install dependencies
4. Copy source code
5. Build TypeScript
6. Create temp directory for GRIB files



**Dependencies**:

- Waits for `db-migrate` to complete successfully
- Waits for `postgrex` to be healthy



### Resource Management

**Temporary Files**:

- Wind Service creates temp files in `/app/temp`
- Files cleaned up after processing
- GRIB files, JSON files removed after conversion

**Database Storage**:

- Historical data retained (no automatic cleanup)
- Composite primary key prevents duplicates
- Indexes optimize query performance


---

## System Integration Summary

### Data Pipeline

1. **Wind Service** downloads GFS data every 3 hours
2. Data processed and stored in **PostgreSQL**
3. **API Service** queries database for latest forecast
4. API transforms data to optimized grid format
5. **Website** fetches data and visualizes on map

### Key Design Decisions

1. **Isolated Wind Service**: Separate service for data acquisition allows independent scaling and deployment
2. **Smart Fetching**: Prevents redundant downloads by checking data freshness
3. **Grid Format**: Optimized format reduces API response size and simplifies client-side processing
4. **Historical Retention**: All forecast data retained for potential future analysis
5. **Spatial Indexing**: Database indexes optimized for bounding box queries
6. **Debounced Reloads**: Prevents API overload during map navigation

### Performance Characteristics

- **Data Resolution**: 1° (approximately 111 km)
- **Update Frequency**: Every 3 hours
- **Grid Points**: ~64,800 points globally (360 × 181)
- **Batch Size**: 10,000 records per database insert
- **API Response**: Optimized grid format (typically < 1 MB for regional queries)
- **Visualization**: Real-time rendering with leaflet-velocity
