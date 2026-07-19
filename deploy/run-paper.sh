#!/usr/bin/env bash
# 업비트 모의투자(PAPER) 봇 실행 스크립트
# 사용법: ./deploy/run-paper.sh          (포그라운드 실행)
#        nohup ./deploy/run-paper.sh > trading.log 2>&1 &   (백그라운드 실행)
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew bootJar

# PAPER 모드 강제 + 앱 시작과 동시에 자동매매 엔진 시작
exec java -jar build/libs/EVCar-0.0.1-SNAPSHOT.jar \
    --upbit.trading.mode=PAPER \
    --upbit.trading.auto-start=true \
    "$@"
