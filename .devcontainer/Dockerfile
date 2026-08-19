FROM mcr.microsoft.com/devcontainers/base:ubuntu

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=${ANDROID_SDK_ROOT}
ENV PATH=${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/emulator:${PATH}
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"

RUN apt-get update && apt-get install -y --no-install-recommends \
        openjdk-17-jdk-headless \
        unzip curl wget zip \
        libgl1 libpulse0 \
    && rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    url=$(curl -fsSL https://developer.android.com/studio \
        | grep -oE 'https://dl\.google\.com/android/repository/commandlinetools-linux-[0-9]+_latest\.zip' \
        | head -n1); \
    mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"; \
    curl -fsSL "$url" -o /tmp/cmdline-tools.zip; \
    unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_SDK_ROOT}/cmdline-tools"; \
    mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest"; \
    rm /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;android-36" \
        "build-tools;36.0.0" \
        "extras;google;m2repository" \
        "extras;android;m2repository" > /dev/null

RUN chown -R vscode:vscode "${ANDROID_SDK_ROOT}"
USER vscode
