#!/bin/bash

# --- Configuration ---
# Path to the payment processor's compose file directory
PROCESSOR_DIR="/Users/felipeshirmann/Documents/Projects.tmp/rinha-classification/rinha-de-backend-2025/payment-processor"
# Path to your Java application's project directory
PROJECT_DIR="/Users/felipeshirmann/Documents/Projects.tmp/Java/rinha-v4"
# Base URL for your running application
APP_URL="http://localhost:9999"
# Wait timeout for your application to start, defaults to 60s
STARTUP_TIMEOUT=${1:-60}


# --- Cleanup Function ---
# This function is called automatically when the script exits, for any reason.
cleanup() {
  echo "\n--------------------------------------------------"
  echo "Stopping and removing processor containers..."
  # Go to the directory and run podman compose down.
  # The -v flag removes named volumes, ensuring a clean state for the next run.
  (cd "$PROCESSOR_DIR" && podman compose down -v)
  (cd "$PROJECT_DIR" && podman compose -f docker-compose.dev.yml down -v)
  echo "Cleanup complete."
}


# --- Main Script Logic ---

# Set a trap: when the script receives the EXIT signal, run the cleanup function.
# This ensures containers are always stopped, even if the script fails or is interrupted.
trap cleanup EXIT

echo "Starting processor containers in the background..."
(cd "$PROCESSOR_DIR" && podman compose up -d)
(cd "$PROJECT_DIR" && podman compose -f docker-compose.dev.yml up -d)

echo "Waiting 30 seconds for processors to initialize..."
sleep 30

# Set the context to the project directory to handle local files
cd "$PROJECT_DIR"
rm -f .test_successful

echo "Waiting for the application to be ready at ${APP_URL}/payments-summary (timeout: ${STARTUP_TIMEOUT}s)..."

# Loop to check if the application is responsive.
for (( i=0; i<STARTUP_TIMEOUT; i+=2 )); do
  if curl -s -f --max-time 1 "${APP_URL}/payments-summary" > /dev/null; then
    echo "Application is up!"

    # --- Test Logic ---
    echo "--------------------------------------------------"
    echo "Purging any previous test data to ensure a clean state..."
    curl -s -X POST "${APP_URL}/purge-payments" -H "Content-Type: application/json"
    echo -e "\nData purged. Starting new test."

    echo "--------------------------------------------------"
    echo "Exercising the /payments endpoint for 15 seconds..."

    # This loop runs for 15 seconds, sending a new payment every second.
    # This gives the GraalVM agent plenty of time to observe the calls.
    END_TIME=$((SECONDS + 15))
    while [ $SECONDS -lt $END_TIME ]; do
      UUID=$(uuidgen)
      curl -s -X POST "${APP_URL}/payments" \
        -H "Content-Type: application/json" \
        -d "{\"correlationId\": \"$UUID\", \"amount\": 10.00}"
      echo -n "."
      sleep 1
    done

    echo "\n--------------------------------------------------"
    echo "Waiting 2 seconds for the worker to finish processing..."
    sleep 2

    echo "Fetching final summary..."
    curl -v "${APP_URL}/payments-summary"

    echo "\n--------------------------------------------------"
    echo "Purging data after this test run..."
    curl -s -X POST "${APP_URL}/purge-payments" -H "Content-Type: application/json"

    echo "\n--------------------------------------------------"
    echo "Test script completed successfully."
    # Creates the success flag file
    touch .test_successful
    exit 0 # Exit with success
  fi

  echo -n "." # Print a dot to show progress
  sleep 2
done

echo "\nError: Application did not start within the ${STARTUP_TIMEOUT}s timeout."
exit 1 # Exit with an error code