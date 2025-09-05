# Claude Code Instructions

## Shell Script Execution

### Purpose: Educational Command Explanations
The primary goal of these detailed explanations is **to help you learn** how shell commands work, their options, and their effects. Every explanation should be a learning opportunity.

When executing shell scripts or bash commands, always:

1. **Provide a clear, detailed explanation** of what the command does before running it
2. **Break down complex commands** into their components and explain each part
3. **Warn about potential side effects** such as:
   - File system modifications
   - Network requests
   - System configuration changes
   - Package installations or updates
4. **Explain the expected output** and what success/failure looks like
5. **For multi-step commands**, explain the purpose of each step
6. **Add educational insights** such as:
   - Alternative approaches or variations of the command
   - Common use cases and best practices
   - Potential gotchas or things to watch out for
   - Related commands that might be useful
   - Tips for troubleshooting if the command fails

## Required Format

Before executing any bash command, use this format:

```
I'm going to run the following command:
`[command here]`

This command will:
- [Primary action in clear terms]
- [Secondary actions or filters]
- [Output limitations or formatting]
- [Purpose]: [Why this command is being run]

Detailed Breakdown:

Part 1: [First command or section]
- [flag/option] - [Detailed explanation of what this does]
- [argument] - [What this argument represents]
- [etc.]

Part 2: [Piped command or next section if applicable]
- [command] - [What this command does]
- [flags] - [Explanation of each flag]
- [etc.]

[Warn about any potential issues, side effects, or important notes]

Learning Tips:
- [Alternative commands or approaches]
- [When to use this vs other options]
- [Common variations or related commands]
- [Troubleshooting advice if it fails]
```

## Example

```
I'm going to run the following command:
`find . -type f -name "*.ts" | grep -E "controller|service" | head -10`

This command will:
- Search for TypeScript files in the current directory and all subdirectories
- Filter results to only show files with paths containing "controller" or "service"
- Limit output to the first 10 matching files
- Purpose: Locate TypeScript controller and service files in the project

Detailed Breakdown:

Part 1: find . -type f -name "*.ts"
- find . - Start searching from current directory (.)
- -type f - Only find regular files (not directories, links, etc.)
- -name "*.ts" - Match files ending with .ts (TypeScript files)

Part 2: | grep -E "controller|service"
- | - Pipe operator, sends output from find to grep
- grep -E - Use extended regular expressions for pattern matching
- "controller|service" - Match lines containing either "controller" or "service"

Part 3: | head -10
- | - Pipe operator
- head -10 - Display only the first 10 lines of output

Note: This command is read-only and will not modify any files.

Learning Tips:
- Alternative: Use `fd ".*\.(controller|service)\.ts$"` if you have fd installed (faster)
- Use `-iname` instead of `-name` for case-insensitive matching
- Replace `head -10` with `wc -l` to count total matches instead
- If no results, check if you're in the right directory with `pwd`
```

## Project-Specific Commands

For this project, common commands that should be explained include:
- npm/yarn commands for dependency management
- Build and development server commands
- Git operations
- Database migrations or queries
- Testing and linting commands
- Deployment or CI/CD related commands

## Safety Considerations

- Always verify the current directory before running commands that affect files
- Check for existing files before creating new ones
- Confirm destructive operations (deletions, overwrites) before proceeding
- Explain any security implications of commands that handle credentials or sensitive data

## SQL Learning and Assistance

### Purpose: Educational SQL Explanations
The user wants practical help with SQL and database concepts for learning purposes. When working with SQL:

1. **Explain SQL queries in detail** - break down complex queries into understandable parts
2. **Provide practical examples** - show how SQL concepts apply to real problems
3. **Explain database concepts** - clarify terms like CTEs, aggregations, joins, indexes
4. **Show query optimization tips** - explain why certain approaches are more efficient
5. **Use analogies** - relate SQL concepts to everyday concepts when helpful
6. **Explain query execution flow** - how the database processes the query step by step

### Required Format for SQL Explanations

When explaining SQL queries, use this format:

```
SQL Query Explanation:

**What this query does:**
[High-level purpose in simple terms]

**Step-by-step breakdown:**
1. [First logical step the database takes]
2. [Next step]
...

**Detailed line-by-line:**
- Line X: [Specific SQL clause] - [What it does and why]
- Line Y: [SQL function] - [How it works, what it returns]
...

**Key SQL concepts used:**
- [Concept 1]: [Simple explanation with analogy if helpful]
- [Concept 2]: [Explanation]

**Performance notes:**
- [Why this approach is efficient/inefficient]
- [Alternative approaches if applicable]

**Practical example with sample data:**
[Show how the query would process actual data]
```

## Backend TypeScript Types Structure

This section documents the organized typing structure implemented in the API backend to maintain consistency and type safety across the project.

### Directory Structure

**Type Files Location:** `apps/api/src/types/`

The types are organized by domain following a domain-driven design approach:

```
apps/api/src/types/
├── index.ts                        # Barrel export for all types
├── shared/
│   ├── database.types.ts          # Database operation types (QueryResult, PaginatedResult)
│   └── geojson.types.ts           # GeoJSON types for mapping functionality
├── location/
│   └── location.types.ts          # Location service result types
├── measurement/
│   ├── measurement.types.ts       # Measurement service types and query interfaces
│   └── cluster.types.ts           # Measurement clustering types
└── tasks/
    └── openaq.types.ts            # OpenAQ API response types
```

### Type Categories

**1. Pure Types (go in `types/` directory):**
- Service method return types
- API response interfaces
- Query parameter interfaces
- Utility types and generic helpers
- External API data structures (OpenAQ, etc.)

**2. Entities/DTOs (stay with modules):**
- Database entities (*.entity.ts)
- Data Transfer Objects (*.dto.ts)  
- Request/Response models (*.model.ts)
- Domain models with business logic

### Key Type Files and Their Purpose

**Shared Types:**
- `database.types.ts` - Generic database operation types, pagination interfaces
- `geojson.types.ts` - GeoJSON properties for measurements and clusters

**Domain-Specific Types:**
- `measurement.types.ts` - Service result types, query parameters for measurements
- `location.types.ts` - Location service results, including CigarettesSmokedResult
- `openaq.types.ts` - External OpenAQ API structure types
- `cluster.types.ts` - Measurement clustering data structures

### Typing Conventions Established

1. **Eliminate `any` types** - All previous `any` types have been replaced with proper interfaces
2. **Service method return types** - All service methods now have explicit return type annotations
3. **Query parameter interfaces** - Structured interfaces for area-based and cluster-based queries
4. **Generic pagination** - Reusable `PaginatedResult<T>` type for consistent pagination
5. **External API typing** - Proper interfaces for OpenAQ API responses to ensure data integrity

### Guidelines for Future Type Additions

1. **Domain Organization:** Place new types in the appropriate domain folder under `types/`
2. **Barrel Exports:** Always add new type exports to `types/index.ts`
3. **Naming Convention:** Use descriptive names ending with appropriate suffixes:
   - `*Result` for service method return types
   - `*Query` for query parameter interfaces
   - `*Response` for external API responses
4. **Entity Separation:** Keep entities/DTOs with their respective modules, only move pure types to `types/` directory
5. **Generic Types:** Use generic types for reusable patterns (like `PaginatedResult<T>`)

### Type Safety Achievements

- **100% `var` elimination** - All `var` declarations replaced with `let`/`const`
- **Complete `any` type removal** - All `any` types replaced with proper interfaces
- **Service return typing** - All service methods have explicit return types
- **External API safety** - OpenAQ API responses are fully typed

This structure ensures type safety, maintainability, and consistency across the backend API while following domain-driven design principles.