#!/bin/bash

# Test data for development - not secure passwords
set -eux

declare -r HOST="keycloak:8080/auth"

wait-for-url() {
    echo "Testing $1"
    timeout -s TERM 45 bash -c \
    'while [[ "$(curl -s -o /dev/null -L -w ''%{http_code}'' ${0})" != "200" ]];\
    do echo "Waiting for ${0}" && sleep 2;\
    done' ${1}
    echo "OK!"
    curl -I $1
}

wait-for-url http://${HOST}

./kcadm.sh config credentials --server http://${HOST} --realm master --user admin --client admin-cli --password Pa55w0rd

./kcadm.sh create realms -s realm=HAPIFHIR -s enabled=true

./kcadm.sh create clients -r HAPIFHIR -s clientId=hapifhir-client -s enabled=true -s directAccessGrantsEnabled=true -s publicClient=true

./kcadm.sh create users -r HAPIFHIR -s username=test -s enabled=true

./kcadm.sh set-password -r HAPIFHIR --username test --new-password Pa55w0rd

