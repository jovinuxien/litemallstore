# Elasticsearch with the analysis-stempel plugin baked in.
#
# The dev stack (docker-compose/elasticsearch/docker-entrypoint-es.sh) installs
# this plugin on EVERY container start, which means each boot makes a network
# call to the Elastic plugin repo. In prod that turns a restart into an outage
# whenever that repo is slow, rate-limits, or is unreachable. Baking it into the
# image makes the plugin an immutable property of the artifact.
FROM docker.elastic.co/elasticsearch/elasticsearch:7.17.25

RUN elasticsearch-plugin install --batch analysis-stempel
