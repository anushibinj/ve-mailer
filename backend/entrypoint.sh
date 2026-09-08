#!/bin/sh
set -e

# Translate standard proxy env vars into JVM system properties
if [ -n "$HTTP_PROXY" ] || [ -n "$http_proxy" ]; then
    RAW_PROXY="${HTTP_PROXY:-$http_proxy}"
    PROXY_HOST=$(echo "$RAW_PROXY" | sed -e 's|^https\?://||' -e 's|/$||' -e 's|:.*||')
    PROXY_PORT=$(echo "$RAW_PROXY" | sed -e 's|.*:||' -e 's|/$||')
    JAVA_PROXY_OPTS="-Dhttp.proxyHost=$PROXY_HOST -Dhttp.proxyPort=$PROXY_PORT"
fi

if [ -n "$HTTPS_PROXY" ] || [ -n "$https_proxy" ]; then
    RAW_PROXY="${HTTPS_PROXY:-$https_proxy}"
    PROXY_HOST=$(echo "$RAW_PROXY" | sed -e 's|^https\?://||' -e 's|/$||' -e 's|:.*||')
    PROXY_PORT=$(echo "$RAW_PROXY" | sed -e 's|.*:||' -e 's|/$||')
    JAVA_PROXY_OPTS="$JAVA_PROXY_OPTS -Dhttps.proxyHost=$PROXY_HOST -Dhttps.proxyPort=$PROXY_PORT"
fi

if [ -n "$NO_PROXY" ] || [ -n "$no_proxy" ]; then
    RAW_NOPROXY="${NO_PROXY:-$no_proxy}"
    NON_PROXY=""
    OLD_IFS="$IFS"
    IFS=','
    for entry in $RAW_NOPROXY; do
        case "$entry" in
            .*) entry="*${entry}" ;;
            */*) continue ;;               # skip CIDR ranges, Java glob can't express them
        esac
        if [ -z "$NON_PROXY" ]; then
            NON_PROXY="$entry"
        else
            NON_PROXY="$NON_PROXY|$entry"
        fi
    done
    IFS="$OLD_IFS"
    if [ -n "$NON_PROXY" ]; then
        JAVA_PROXY_OPTS="$JAVA_PROXY_OPTS -Dhttp.nonProxyHosts=$NON_PROXY"
    fi
fi

export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS $JAVA_PROXY_OPTS"

exec "$@"