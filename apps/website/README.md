# AirGradient Map Frontend

This is the frontend application for AirGradient Map, built with and Nuxt.js.

## Development Setup

For the complete monorepo setup and Docker configuration, please refer to the [root README.md](../../README.md).

### Frontend-Specific Development

#### Option 1: Using Docker (Recommended)

The frontend is included in the main Docker Compose setup. From the root directory:

```bash
docker compose --env-file apps/api/.env.development -f docker-compose-dev.yml up
```

The frontend will be available at `http://localhost:3000`

#### Option 2: Direct Node.js Setup

If you prefer to run only the frontend locally:

Prerequisites:

- Node.js v22 or higher

1. **Install dependencies**:

```bash
npm install
```

2. **Start the development server**:

```bash
npm run dev
```

The application will be available at `http://localhost:3000`

**Note:** When running frontend-only, you'll need to ensure the API is running separately or configure the frontend to point to a running API instance.

## Frontend Development Commands

```bash
# Start development server without Docker
npm run dev

# Run tests
npm run test

# Format code
npm run format

# Build for production
npm run build
```

## Project Structure

```bash
website/
├── components/     # Vue components
├── pages/         # Application pages
├── public/        # Static files
├── layouts/       # Page layouts
├── assets/        # Assets (images, styles, etc.)
├── store/         # State management
├── utils/         # Utility functions
├── types/         # TypeScript types
├── Dockerfile     # Production Docker configuration
└── docker-compose.yml  # Development Docker configuration
```

## Frontend Troubleshooting

Common frontend-specific issues:

1. **Port already in use**:

```bash
# Check what's using port 3000
lsof -i :3000
# Kill the process
kill -9 <PID>
```

2. **Node modules issues**:

```bash
# Remove node_modules and reinstall
rm -rf node_modules
npm install
```

3. **Build cache issues**:

```bash
# Clear Nuxt cache
rm -rf .nuxt
rm -rf .output
npm run dev
```

## License

This project is licensed under the GNU General Public License v3.0 (GPL-3.0).
