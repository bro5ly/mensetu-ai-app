FROM mcr.microsoft.com/devcontainers/base:ubuntu

# Install Java 21
RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && apt-get -y install --no-install-recommends \
    openjdk-21-jdk \
    gradle \
    && apt-get clean -y && rm -rf /var/lib/apt/lists/* \
    && ln -sfT /usr/lib/jvm/java-21-openjdk-$(dpkg --print-architecture) /usr/lib/jvm/java-21-openjdk

# Install Node.js 20
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs \
    && apt-get clean -y && rm -rf /var/lib/apt/lists/*

# Install additional tools
RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && apt-get -y install --no-install-recommends \
    git \
    curl \
    wget \
    postgresql-client \
    && apt-get clean -y && rm -rf /var/lib/apt/lists/*

# Set JAVA_HOME (architecture-independent symlink created above; works on amd64/arm64)
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Create workspace directory
RUN mkdir -p /workspace && chown vscode:vscode /workspace

USER vscode
