#!/bin/bash

if [ -e docker-compose.custom.yml ]; then
    CUSTOM_YML_PARAM="-f docker-compose.custom.yml"
fi

# The base is docker-compose.yml. It was `docker-compose.base.yml` upstream and
# these scripts were never updated after the rename, so the documented local
# bring-up has been broken (`no such file`) for its whole life here.
docker compose -f docker-compose.yml -f docker-compose.local.yml $CUSTOM_YML_PARAM "$@"
