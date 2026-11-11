# See ci-build-publish.yml which sets baseImage=hmcts/apm-services:25-jre and agentDemand:ubuntu-j25
# azure pipeline replaces $BASE_IMAGE with crmdvrepo01.azurecr.io + $baseImage
# This image has the hmcts self signing certificate authority added to truststore so we dont need to worry about about the certs
# If pulling this locally we need to authenticate to acr ... az login; az acr login -n crmdvrepo01
ARG BASE_IMAGE
# Fallback pinned to digest so the tag can't be silently repointed upstream
# The default is only used for a local build; ADO substitutes the ACR base. Kept on the same
# Ubuntu release as that base (24.04) so a local image resembles the deployed one -- the
# digest this replaced was Ubuntu 26.04, which carries a pebble binary we never deploy.
FROM ${BASE_IMAGE:-eclipse-temurin:25-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce}

# install curl for debugging; upgrade openssl, libbrotli1, and apt ahead of the base image.
# The CI/GHCR image is built from the pinned eclipse-temurin digest below, which already
# ships the patched versions; these lines are a safety net for the ADO-deployed image, which
# is built from crmdvrepo01.azurecr.io/hmcts/apm-services:25-jre (a private HMCTS base whose
# patch level we cannot verify here) — without them, CVE-2026-84782 (openssl),
# CVE-2026-111497 (libbrotli1), and CVE-2026-109518 (apt/libapt-pkg) may remain unpatched.
RUN apt-get update \
    && apt-get install -y curl \
    && apt-get install -y --only-upgrade openssl libssl3t64 libbrotli1 apt libapt-pkg6.0t64 libpcre2-8-0 \
    && rm -rf /var/lib/apt/lists/*

# run as non-root ... group and user "app"
RUN groupadd -r app && useradd -r -g app app
WORKDIR /app

# ---- Application files ----
COPY docker/* /app/
COPY build/libs/*.jar /app/
COPY lib/applicationinsights.json /app/

USER app
ENTRYPOINT ["/bin/sh","./startup.sh"]