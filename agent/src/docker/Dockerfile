# --- build image ---

FROM eclipse-temurin:21 AS build

# We need to manually pass this enviroment variable through to make Gradle determine the correct version number (TS-42362)
ARG GITHUB_REF
ENV GITHUB_REF=${GITHUB_REF}

ADD . /src
WORKDIR /src
RUN ./gradlew :agent:shadowJar

# --- production image ---
FROM alpine:latest

RUN mkdir -p /agent
COPY --from=build /src/agent/build/libs/teamscale-jacoco-agent.jar /agent

VOLUME /agent

# make the image do nothing when started
# except if /transfer is mounted, then copy the agent there and exit immediately
# this lets us use the image as an init container in Kubernetes
COPY <<EOT /entrypoint.sh
#!/bin/sh
if [ -e /transfer ]; then
  cp -r /agent/teamscale-jacoco-agent.jar /transfer
  exit 0
fi
trap : TERM INT
sleep infinity & wait
EOT
RUN chmod +x /entrypoint.sh

CMD ["/entrypoint.sh"]
