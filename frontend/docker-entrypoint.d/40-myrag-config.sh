#!/bin/sh
set -eu

envsubst '${OIDC_BROWSER_AUTHORITY} ${OIDC_CLIENT_ID} ${OIDC_SCOPES} ${OIDC_API_TOKEN_SOURCE} ${OIDC_PRINCIPAL_CLAIM} ${OIDC_ROLES_CLAIM}' \
  < /usr/share/nginx/html/config.template.js \
  > /usr/share/nginx/html/config.js
