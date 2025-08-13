# === Stage 1: Build Environment ===
# Use the official Oracle GraalVM image, which provides a stable,
# integrated environment with all necessary tools for native compilation.
FROM container-registry.oracle.com/graalvm/native-image:21 AS builder

ENV LC_ALL=C.utf8

WORKDIR /app

# Copy only the necessary files to leverage Docker's layer caching.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Make the Maven wrapper executable and download dependencies.
# This layer will be cached if the pom.xml file does not change.
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline

# Copy the rest of the application source code.
COPY src ./src

# Build the native executable using the 'native' Maven profile.
# Tests are skipped to speed up the build process.
RUN ./mvnw -Pnative clean package -DskipTests


# === Stage 2: Final Production Image ===
# Start from a minimal and stable Debian base image to ensure compatibility.
FROM debian:12-slim

# Create a dedicated, non-root user for security best practices.
RUN useradd --create-home --uid 1001 --user-group nonroot
USER nonroot:nonroot

WORKDIR /app

# Copy only the final executable from the builder stage into the final image.
# This keeps the production image small and secure.
COPY --from=builder --chown=nonroot:nonroot /app/target/rinha-reativa .

# Expose the port the application will run on.
EXPOSE 9999

# Set the default command to run the application when the container starts.
CMD ["./rinha-reativa"]
