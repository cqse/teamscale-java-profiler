#!/bin/sh
# When /transfer exists (shared volume), copy the agent JAR there and exit.
# This supports the Kubernetes init container pattern where this image provides
# the agent JAR to the application container via a shared volume.
if [ -e /transfer ]; then
  cp -r /agent/teamscale-jacoco-agent.jar /transfer
  exit 0
fi
# Otherwise, keep the container alive indefinitely as a sidecar.
trap : TERM INT
sleep infinity & wait
