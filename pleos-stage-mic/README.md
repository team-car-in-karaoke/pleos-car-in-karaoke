# 📱 카인노래방 마이크 (`pleos-stage-mic`)

현대자동차그룹 **Pleos Connect** 차량용 노래방(**카인노래방 / Car in Karaoke**)과 실시간 연동되는 **Kotlin Multiplatform 무선 스마트 마이크 & 리모컨 애플리케이션**입니다. 공통 UI 및 상태 관리(`:shared`)는 KMP 기반으로 구성되어 있으며(iOS `WKWebView` 기본 브릿지 포함), 초저지연 보컬 수음 및 시스템 연동 기능은 **Android(`:androidApp`) 네이티브 환경에 최적화**되어 있습니다.

## 주요 기술 및 기능
- **안드로이드 네이티브 `AudioRecord` 1:1 무손실 캡처 & `targetSdk = 28` 핫스팟(`swlan0`) 언락:**
  - WebView JS 스레드 병목과 크롬 노이즈 게이트를 우회하여 생생한 보컬 PCM을 1:1 스트리밍하며, 모바일 핫스팟(`swlan0`) 실제 로컬 IP를 추출해 **20~30ms 초저지연 직결**을 지원합니다.
- **3단계 하이브리드 전송 우선순위:**
  1. 로컬 TCP WebSocket 직결 (`:8082` Outbound Listener / `:8080` 차량 서버)
  2. WebRTC `RTCDataChannel` (`vocal-fast-lane` UDP P2P 직결)
  3. 다중 클라우드 WSS 브로커 (`EMQX` / `HiveMQ`) 자동 폴백
- **원터치 `화면 켜짐 유지 ON/OFF` 토글 (`FLAG_KEEP_SCREEN_ON`):**
  - 앱 실행 시 기본 활성화(`☀️ 화면 켜짐 유지 ON`)되어 노래 중 화면 꺼짐을 방지하며, 상단 헤더에서 언제든 `🌙 화면 자동 꺼짐`으로 전환할 수 있습니다.
- **양방향 노래방 점수 발표 & 실시간 프로그레스바 탐색(Seek) & YouTube 공유(`ACTION_SEND`):**
  - 차량 스테이지와 1:1 동기화되는 점수 측정 ON/OFF 및 5초 카운트업 점수 오버레이, 구간 탐색(Seek), 사운드/키/에코/효과음 원격 제어를 제공합니다.

## 빌드 명령어

- **Windows (PowerShell):**
  ```powershell
  .\gradlew.bat :androidApp:assembleDebug
  ```
- **macOS / Linux:**
  ```bash
  ./gradlew :androidApp:assembleDebug
  ```
