#!/bin/sh
set -eu

envsubst '${OIDC_BROWSER_AUTHORITY} ${OIDC_CLIENT_ID} ${OIDC_SCOPES}' \
  < /usr/share/nginx/html/config.template.js \
  > /usr/share/nginx/html/config.js
