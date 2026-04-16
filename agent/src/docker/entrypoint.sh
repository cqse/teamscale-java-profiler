#!/bin/sh
if [ -e /transfer ]; then
  cp -r /agent/teamscale-jacoco-agent.jar /transfer
  exit 0
fi
trap : TERM INT
sleep infinity & wait
