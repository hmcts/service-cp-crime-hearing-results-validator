#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:4550}"
REPORT_DIR="build/reports/gatling"

echo "=== NFT Performance Tests ==="
echo "Target: $BASE_URL"
echo ""

# Check service is up
echo "Checking service health..."
if ! curl -sf "$BASE_URL/actuator/health" > /dev/null 2>&1; then
  echo "ERROR: Service is not running at $BASE_URL"
  echo "Start it with: docker compose up -d"
  exit 1
fi
echo "Service is healthy."
echo ""

# Build Gatling classes
echo "Building Gatling classes..."
./gradlew gatlingClasses -q
echo ""

# Run Capacity Simulation (pipeline gate — with assertions)
echo "=== Running Capacity Simulation (pipeline gate) ==="
./gradlew gatlingRun \
  --simulation=uk.gov.hmcts.cp.simulation.CapacitySimulation \
  -Dgatling.baseUrl="$BASE_URL"
echo ""

# Run Stress Simulation (exploratory — no assertions)
echo "=== Running Stress Simulation (exploratory) ==="
./gradlew gatlingRun \
  --simulation=uk.gov.hmcts.cp.simulation.StressSimulation \
  -Dgatling.baseUrl="$BASE_URL"
echo ""

echo "=== Reports ==="
ls -dt "$REPORT_DIR"/*/ 2>/dev/null | head -2 | while read -r dir; do
  echo "  $dir/index.html"
done
