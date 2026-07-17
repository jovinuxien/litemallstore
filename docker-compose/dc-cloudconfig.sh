#!/bin/bash

if [ -e docker-compose.custom.yml ]; then
    CUSTOM_YML_PARAM="-f docker-compose.custom.yml"
fi

# See dc-local.sh: the base is docker-compose.yml, not the never-existing
# docker-compose.base.yml.
docker compose -f docker-compose.yml -f docker-compose.cloudconfig.yml -f docker-compose.frontend.yml $CUSTOM_YML_PARAM "$@"
