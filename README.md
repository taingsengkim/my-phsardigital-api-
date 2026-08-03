# PhsarDigital API

Spring Boot 4 API built with Java 25, Gradle, PostgreSQL, Keycloak, and MinIO.

## Local infrastructure

Create the local environment file and start the supporting services:

```bash
cp phsardigital-docker/.env.example phsardigital-docker/.env
docker compose -f phsardigital-docker/docker-compose.yml up -d
```

Replace every `change-me` value before starting the stack. The API also needs the
database, Keycloak, and MinIO variables defined in
`src/main/resources/application.yaml`.

Run the tests with:

```bash
./gradlew test --no-daemon
```

## CI/CD deployment

The workflow in `.github/workflows/ci-cd.yml` performs three stages:

1. Every pull request to `main` is tested on Java 25 against PostgreSQL 18.4.
2. A successful push to `main` builds the Docker image and publishes the commit
   SHA and `latest` tags to GitHub Container Registry (GHCR).
3. The exact SHA image is deployed to a Docker host over SSH. Docker Compose
   waits for the API health check and rolls back the API image if startup fails.

The deployment target is expected to be an Ubuntu/Linux host with Docker Engine,
Docker Compose v2.20 or newer, and an SSH user allowed to run Docker commands.
The production Compose file deploys only the Spring API; it reuses the existing
PostgreSQL, Keycloak, MinIO, and Nginx Proxy Manager containers.

### 1. Prepare the Docker host

Create a separate deployment directory for the API:

```bash
mkdir -p /home/ubuntu/istad/phsardigital
sudo usermod -aG docker ubuntu
```

Sign out and back in after changing Docker group membership, then verify
`docker compose version` as the deploy user.

### 2. Configure the GitHub production environment

Create an environment named `production` in the GitHub repository and add these
environment variables:

| Variable | Example |
| --- | --- |
| `DEPLOY_HOST` | `51.79.146.203` (optional; currently the workflow default) |
| `DEPLOY_USER` | `ubuntu` (optional; currently the workflow default) |
| `DEPLOY_PORT` | `22` |
| `DEPLOY_PATH` | `/home/ubuntu/istad/phsardigital` (optional; currently the workflow default) |
| `PRODUCTION_URL` | `https://api.example.com` |

Add these environment secrets:

| Secret | Value |
| --- | --- |
| `DEPLOY_SSH_KEY` | Private key for the deploy user |
| `DEPLOY_KNOWN_HOSTS` | Verified `known_hosts` entry for the deployment server |

Generate the `known_hosts` line from a trusted network, verify its fingerprint
against the server console, and save the complete line as the secret. For a
non-default SSH port, the entry must use the `[host]:port` form.

### 3. Create the server environment file

The first deployment uploads `.env.example` to `DEPLOY_PATH` and stops safely if
`.env` is missing. On the server:

```bash
cd /home/ubuntu/istad/phsardigital
cp .env.example .env
chmod 600 .env
```

Replace every placeholder in `.env`. Keep this file only on the server; it is
ignored by Git and is never copied back into GitHub Actions.

Use the same PostgreSQL, Keycloak client, and MinIO credentials as the existing
containers. Do not paste these values into GitHub or commit `.env`.

The API joins `DOCKER_NETWORK`, which defaults to `phsardigital`. Nginx Proxy
Manager can then forward to `phsardigital-api:8999`. If an existing dependency
is not attached to that network, either attach it or use its published host port,
for example `MINIO_INTERNAL_URL=http://host.docker.internal:9000`.

### 4. Deploy

Merge the branch into `main`, or manually run the `CI/CD` workflow on `main`.
The production environment can be configured with required reviewers to add an
approval gate before the SSH deployment starts.

## Secret rotation

Earlier versions of this repository tracked `phsardigital-docker/.env` and had
credentials in `application.yaml`. Those files are now externalized, but the old
values remain in Git history. Rotate the PostgreSQL, Keycloak, and MinIO
credentials before treating any deployment as production-ready. Rewriting Git
history can be considered separately after the whole team coordinates it.
