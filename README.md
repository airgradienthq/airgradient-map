# AirGradient Map

AirGradient Map is a web application for visualizing and analyzing air quality data.

🌍 Explore it live: [map.airgradient.com](https://map.airgradient.com/)

<p align="center">
  <a href="https://github.com/user-attachments/assets/04455b37-6fe3-4584-b750-f49679d260fa">
    <img src="https://github.com/user-attachments/assets/04455b37-6fe3-4584-b750-f49679d260fa" width="800" alt="Screenshot of the map application">
  </a>
</p>

⚠️ **Important:** This repository is a more scalable replacement for our current [production app](https://www.airgradient.com/map/).  
The old app’s tech stack can’t scale to meet our requirements, so we’ve built this new codebase. First, we’ll migrate all existing features here; once that’s done, we’ll layer on new enhancements and capabilities.

## 💬 Join the Discussion on Discord

Have questions or want to share feedback? Join our community on [Discord](https://discord.com/invite/fWx7muwqCA) to chat, ask questions, and collaborate with other contributors!

---

## Local development

1. **Clone the repository**:

```bash
git clone https://github.com/airgradienthq/airgradient-map
cd airgradient-map
```

2. **Configure .env configuration**

You have an `.env.development.example` file in `apps/api`.

Make a copy next to it and name it `.env.development`.

From `.env.development` the only necessary thing that needs to be changed is `API_KEY_OPENAQ`.  
To get the key, please follow steps from OpenAQ [here](https://docs.openaq.org/using-the-api/api-key)

3. **Run the containers**

We have one docker compose file to build and run 3 containers:

- `postgrex-mono`: the database
- `mapapi-mono`: the backend
- `website-mono`: the frontend

To spin them up, run from the root of this repo:

```bash
docker compose --env-file apps/api/.env.development -f docker-compose-dev.yml up
```

This automatically builds and starts the necessary containers. When developing and changing source files, the api service automatically reloads the source files. Use the `--build` option when you change npm dependencies and need to rebuild the image. Optionally use the `-d` option for running detached in the background.

To stop the services, run:

```sh
docker compose --env-file apps/api/.env.development -f docker-compose-dev.yml down
```

4. **Setup Database**

This project uses [Knexjs](https://knexjs.org/) for database migrations and seeding to keep the schema and test data consistent with project changes.

> **⚠️ Important for Existing Developers:** 
>
> If you’ve previously run this project before migrations were introduced, it’s recommended to start fresh and make sure removing the existing Postgres volume:
> 
> ```bash
> docker volume rm airgradient-map_pgdata
> ```

- Run Migrations

Create/update tables with the latest schema:

```bash
docker exec -it mapapi-mono npx knex migrate:latest --knexfile knexfile.ts
```

- Seed Data

Populate tables with sample/initial data:

```bash
$ docker exec -it mapapi-mono npx knex seed:run --knexfile knexfile.ts
```

Example Output:

```
Requiring external module ts-node/register
Seeded 1761 owner
Seeded 14176 locations with PostgreSQL timestamps
Processing 65680 measurement records...
Inserted 1000 of 65680 records
Inserted 6000 of 65680 records
Inserted 11000 of 65680 records
Inserted 16000 of 65680 records
Inserted 21000 of 65680 records
Inserted 26000 of 65680 records
Inserted 31000 of 65680 records
Inserted 36000 of 65680 records
Inserted 41000 of 65680 records
Inserted 46000 of 65680 records
Inserted 51000 of 65680 records
Inserted 56000 of 65680 records
Inserted 61000 of 65680 records
Inserted 65680 of 65680 records
Seeded 65680 measurements mapped to the last 6 hours
Ran 3 seed files
```

- Verify Database Setup 

Check that the data has been seeded correctly:

```bash
docker exec -it postgrex-mono psql -U postgres -d agmap -c "select count(*) from location;"
```

Expected result (row count may differ if seed data changes):

```bash
 count 
-------
 14176
(1 row)
```

5. **Check the UI**

The application is running on `http://localhost:3000/` and calls `http://localhost:3001/` for the backend. You should see the map with the data showing up. 

Please note that the compile log shows running on `http://0.0.0.0:3000/` but this will cause CORS issues. So make sure you use `http://localhost:3000/`.

---

## 📆 Contributing

We welcome contributions from the community!

If you're new to the project:
- Please start by forking this repository and submitting pull requests from your fork.
- If you become an active contributor, we'll be happy to invite you to join as a collaborator.

We use the [GitHub Project board](https://github.com/users/airgradienthq/projects/1/views/1) to track issues and development workflow.

### Labels
Labels like `P0`, `P1`, and `P2` help guide prioritization. These are visible on the **project board**, not directly in the issues list.


### Getting an Issue Assigned
If you'd like to work on an issue:
- Leave a comment on the issue stating that you'd like to take it.
- A maintainer will assign the issue to you.

<p align="center">
  <a href="https://github.com/user-attachments/assets/04455b37-6fe3-4584-b750-f49679d260fa">
   <img width="837" alt="Contributors communication" src="https://github.com/user-attachments/assets/75698d8e-46d4-4887-bedf-bbe44ff229df" />
  </a>
</p>


### Workflow Status
If you're working on an issue or it's ready for review, and the **project board** doesn't reflect that:
- Leave a comment in the issue. We'll move the card to the correct status.

### New Database Schema

Since this project maintain database migrations using [Knexjs](https://knexjs.org/), run below command to create new migration file:

```bash
npx knex migrate:make <MIGRATION_NAME> --knexfile knexfile.ts
```

Please see Knexjs [migration documentation](https://knexjs.org/guide/migrations.html) for more information.

---

Thanks for contributing to AirGradient Map! 🚀


