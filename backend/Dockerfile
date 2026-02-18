# Stage 1: Download dependencies (cached when build.gradle unchanged)
FROM gradle:8.5-jdk17 AS deps
WORKDIR /app
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon --refresh-dependencies || true
RUN gradle compileJava --no-daemon || true

# Stage 2: Build application
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
# Copy cached gradle home from deps stage
COPY --from=deps /home/gradle/.gradle /home/gradle/.gradle
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle build -x test --no-daemon

# Stage 3: Build LibreDWG
FROM debian:bookworm-slim AS libredwg-builder
RUN apt-get update && apt-get install -y \
    git build-essential autoconf automake libtool texinfo \
    && rm -rf /var/lib/apt/lists/*
RUN git clone --depth 1 https://github.com/LibreDWG/libredwg.git /libredwg
WORKDIR /libredwg
RUN sh autogen.sh && ./configure --prefix=/usr/local && make -j$(nproc) && make install

# Stage 4: Final runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    libc6 python3 python3-pip \
    && rm -rf /var/lib/apt/lists/*

RUN pip3 install --no-cache-dir ifcopenshell

COPY --from=libredwg-builder /usr/local/bin/dwg2dxf /usr/local/bin/
COPY --from=libredwg-builder /usr/local/lib/libredwg* /usr/local/lib/
RUN ldconfig

RUN mkdir -p /app/dwg-input /app/dwg-output /tmp/buildquote/ifc \
    && chmod 777 /app/dwg-input /app/dwg-output /tmp/buildquote/ifc

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
