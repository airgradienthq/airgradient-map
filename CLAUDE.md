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