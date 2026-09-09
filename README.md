# BrowserDownloader

Android에서 Chrome 이미지와 공개 X 게시물 미디어를 선택 저장하는 개발 중 앱입니다.
UI/UX 변경 전 기준 버전은 `0.21.0-service-lifecycle`입니다.

- Chrome: 현재 탭 검사 → 결과 팝업 → 선택 저장. 직접 스크롤한 뒤 재검사하면 같은 문서의 결과를 누적합니다.
- X: 게시물 공유 → BrowserDownloader G1 → 미디어 그리드 → 선택 저장. 직접 링크 입력도 지원합니다.
- 저장 폴더: `Download/BrowserDownloader`. 사용할 권한이 있는 미디어만 저장하세요.
- 사이트별 원본 확인 범위가 다릅니다. GIF는 제공 MP4를 보존하고 별도 GIF로 변환합니다.

## 라이선스·배포 상태

Copyright (C) 2026 BrowserDownloader contributors.

BrowserDownloader의 자체 소스 코드·문서·직접 제작 시험 리소스는
**GNU General Public License version 3 only (SPDX: GPL-3.0-only)**로 제공합니다.
이 프로그램은 유용하기를 바라며 배포하지만 상품성이나 특정 목적 적합성을 포함한
어떠한 보증도 제공하지 않습니다. 수정·재배포 조건은 [LICENSE](LICENSE)를 따릅니다.
제3자 코드·리소스·고지에는 각 구성물의 기존 라이선스가 적용됩니다.

`:app → :xmedia`와 독립 `:xprobe`는 GPL-3.0인 youtubedl-android 0.18.1을
사용합니다. 2026-09-09에 기능을 유지하는 GPL 공개 방향을 확정했습니다.

현재 공개 범위는 **소스 코드 저장소**이며 APK는 제공하지 않습니다.
저장소에는 앱 소스, 빌드 설정, 시험 코드 및 고지가 포함됩니다.
내장 yt-dlp 리소스·Gradle wrapper·직접 제작 시험 영상의 출처와 라이선스도 보존합니다.

향후 APK 배포를 위한 Python/QuickJS/전이 구성물의 고지 및 대응 소스 검토는
별도 후속 작업입니다. 해당 네이티브 바이너리는 이 소스 저장소에 포함하지 않습니다.
APK 배포 시에는 해당 태그의 앱 소스뿐 아니라 필요한 의존성 대응 소스와
빌드 입력도 제공해야 합니다. 현재 소스 저장소의 LICENSE 추가만으로 이를
완료했다고 간주하지 않습니다. 빌드 출력과 로컬 시험 APK는 Git에서 제외합니다.
출처·보존 고지는 [THIRD_PARTY_NOTICES](xprobe/THIRD_PARTY_NOTICES.md)를 확인하세요.
기획과 상세 검토 기록은 [AGENTS.md](AGENTS.md)가 지정하는 Obsidian에서 관리합니다.

## 빌드

검증 환경: Windows, JDK 21, Android SDK Platform 36.1 및 Build Tools 36.1.0.
Gradle 9.5.1/Android Gradle Plugin 9.2.1은 프로젝트에 고정돼 있습니다.
`app`은 통합 앱, `xmedia`는 공유 X 코드 라이브러리, `xprobe`는 독립 진단 앱입니다.

```powershell
$env:JAVA_HOME = '<JDK 21 설치 경로>'
$env:ANDROID_HOME = '<Android SDK 설치 경로>'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :xmedia:testDebugUnitTest :app:lintDebug
```

산출물: `app/build/outputs/apk/debug/app-debug.apk`. 릴리스 서명 설정은 포함하지 않습니다.
첫 실행에서 안내에 동의하고 검사 활성화·접근성·알림 설정을 확인하세요.
최소 Android 10(API 29), 대상 API 36이며 모든 지원 API 기기의 시험 완료를 뜻하지 않습니다.

## 시험과 파일 관리

```powershell
python -m unittest discover -s tools -p 'test_*.py'
```

연결 기기 계측은 명시적인 시험 작업에서 실행합니다. 시험이 끝난 뒤에는
[업데이트 전달 검사](tools/verify_apk_update.py)로 정상 APK 설치/접근성 연결을 확인합니다.
일반 빌드에 기기 조작은 포함되지 않습니다.

빌드 출력/APK, 다운로드 파일, 로컬 증거, 스크린샷, 서명키, SDK 경로 및 개발 에이전트
번들은 Git에서 제외합니다. `self_authored.mp4`는 직접 생성한 시험 패턴이며
`ytdlp`는 고정된 상류 zipimport 리소스입니다. 출처와 해시는 고지 문서에 기록합니다.
