#!/bin/bash
# Fast deployment script - no Docker rebuild needed
# Usage: ./deploy.sh [backend|frontend|all]

set -e

SERVER="root@62.171.153.133"
REMOTE_DIR="/root/buildquote"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

deploy_backend() {
    echo -e "${YELLOW}=== Deploying Backend ===${NC}"

    cd backend

    # Build JAR locally (skip tests)
    echo "Building JAR with Gradle..."
    ./gradlew build -x test --no-daemon -q

    JAR_FILE=$(ls build/libs/*.jar 2>/dev/null | grep -v plain | head -1)
    if [ -z "$JAR_FILE" ]; then
        echo -e "${RED}JAR file not found!${NC}"
        exit 1
    fi

    echo "Built: $JAR_FILE"

    # Copy JAR to server
    echo "Uploading JAR to server..."
    scp -o StrictHostKeyChecking=no "$JAR_FILE" "$SERVER:/tmp/app.jar"

    # Replace JAR in container and restart
    echo "Restarting backend service..."
    ssh -o StrictHostKeyChecking=no "$SERVER" << 'ENDSSH'
        # Copy JAR into running container
        docker cp /tmp/app.jar bq-backend:/app/app.jar

        # Restart the container
        docker restart bq-backend

        # Fix postgres password (known issue)
        sleep 5
        docker exec bq-postgres psql -U postgres -c "ALTER USER postgres WITH PASSWORD 'postgres';" 2>/dev/null || true

        # Wait for startup
        echo "Waiting for backend to start..."
        for i in {1..30}; do
            if curl -s http://localhost:8081/api/projects/health > /dev/null 2>&1; then
                echo "Backend is up!"
                break
            fi
            sleep 1
        done
ENDSSH

    cd ..
    echo -e "${GREEN}Backend deployed!${NC}"
}

deploy_frontend() {
    echo -e "${YELLOW}=== Deploying Frontend ===${NC}"

    cd frontend

    # Build Angular app locally
    echo "Building Angular app..."
    npm run build -- --configuration production

    DIST_DIR="dist/buildquote-frontend/browser"
    if [ ! -d "$DIST_DIR" ]; then
        # Try alternative path
        DIST_DIR="dist/buildquote-frontend"
    fi

    if [ ! -d "$DIST_DIR" ]; then
        echo -e "${RED}Build output not found!${NC}"
        exit 1
    fi

    # Sync to server nginx folder
    echo "Syncing to server..."
    rsync -avz --delete -e "ssh -o StrictHostKeyChecking=no" \
        "$DIST_DIR/" \
        "$SERVER:/tmp/frontend-dist/"

    # Copy into container
    ssh -o StrictHostKeyChecking=no "$SERVER" << 'ENDSSH'
        docker cp /tmp/frontend-dist/. bq-frontend:/usr/share/nginx/html/
        docker exec bq-frontend nginx -s reload
        echo "Frontend deployed!"
ENDSSH

    cd ..
    echo -e "${GREEN}Frontend deployed!${NC}"
}

# Main
case "${1:-all}" in
    backend|be|b)
        deploy_backend
        ;;
    frontend|fe|f)
        deploy_frontend
        ;;
    all|"")
        deploy_backend
        deploy_frontend
        ;;
    *)
        echo "Usage: $0 [backend|frontend|all]"
        exit 1
        ;;
esac

echo -e "${GREEN}=== Deployment complete! ===${NC}"
