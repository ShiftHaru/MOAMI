# MOAMI - 모아미

Android에서 Chrome 이미지와 공개 X 게시물 미디어를 선택 저장하는 개발 중 앱입니다.
앱 표시 이름은 **MOAMI - 모아미**입니다. 현재 버전은 `0.32.0-chrome-page-info`입니다. UI/UX 변경 전 기준은 `pre-ui-v0.21.0` 태그입니다.

Chrome 사이트 정보 창에 닫기 버튼이 없어도 전체 주소를 확인합니다. 같은 정보 창인지 재확인한 뒤 시스템 뒤로 가기로 닫으며, 원래 문서와 주소창이 유지된 경우에만 검사합니다. 해당 기기의 수정 후 실사용 확인은 대기 중입니다.

저장된 갤러리 셀에는 체크 배지·강조 테두리가 표시됩니다. 저장 버튼 아래에 완료/미완료 개수를 표시하고 저장 종료 시 스낵바로 결과와 위치를 안내합니다. 표시 상태는 현재 검사 세션 기준이며 기기 파일을 지속 감시하는 다운로드 이력은 아닙니다. 패키지 ID·서명과 Download/BrowserDownloader 저장 폴더는 유지합니다.

Instagram 영상은 실제 MP4 트랙을 확인해 오디오 트랙이 없으면 GIF만, 있으면 MP4로 저장합니다. 무음 오디오 트랙도 오디오 있음으로 처리합니다. GIF 변환 실패·30초/1080p 픽셀 수 제한 초과 시 MP4 대체 저장 없이 실패를 표시합니다. GIF는10fps·256색 변환이며 업로드 원본이 아닙니다. 기존에 저장된 MP4는 삭제하지 않습니다.

Android 13 이상 접근성 설정 단계에서 제한된 설정 허용 안내와 앱 정보 이동을 제공합니다. 시스템 설정에서 사용자가 직접 허용해야 하며, 앱으로 돌아오면 실제 접근성 허용 상태를 다시 확인합니다. 설치 전 Play Protect 차단 해제 기능은 아닙니다.

Instagram 공개 게시물·릴스 공유와 주소 입력을 지원합니다. 팝업 그리드에서 사진·영상을 선택 저장하며 로그인은 지원하지 않습니다. 사진은 제공 후보 중 최대 크기, 영상은 제공 MP4 후보의 실제 해상도를 비교합니다. 업로드 원본을 보장하지 않으며 로그인 요구·요청 제한 시 조회에 실패할 수 있습니다. 기존 접근성·알림 온보딩은 유지합니다. 실기기 공유부터 저장까지의 검증은 별도입니다.

Chrome의 얇고 긴 가장자리 핸들을 짧게 누르면 검사 메뉴가 열립니다. 길게 누른 뒤 위아래로 드래그하면 위치를 바꾸며, 손을 놓으면 저장합니다. 접근성 동작의 ‘위로 이동/아래로 이동’도 지원합니다.

통합 앱은 Material 3 갤러리·튜토리얼·결과 팝업과 Chrome 가장자리 물방울 핸들을 사용합니다. 지원 기기의 배경화면 기반 동적 색상 및 시스템 밝기 모드를 따르며, 미지원 기기는 청록색 테마를 사용합니다. 별도 X 진단 앱의 UI는 변경하지 않습니다.

- 첫 실행: 이용·데이터 처리 안내 동의 → 접근성 설정 → 실행 알림 허용 → 갤러리. 기존 사용자도 새 안내에 동의해야 합니다.
- Chrome: 서랍 ON → 현재 탭 검사 → 결과 팝업 → 선택 저장. 같은 문서의 재검사는 누적합니다. 다른 출처로 결과를 교체하면 새 수집입니다.
- 웹주소 입력: 공개 HTML의 이미지 주소를 직접 수집합니다. 로그인·자바스크립트 실행·CSS 배경·iframe 내부는 지원하지 않습니다.
- X: 게시물 공유 → MOAMI - 모아미 → 미디어 그리드 → 선택 저장. 직접 링크 입력도 지원합니다.
- 저장 폴더: `Download/BrowserDownloader`. 사용할 권한이 있는 미디어만 저장하세요.
- 사이트별 원본 확인 범위가 다릅니다. X GIF는 제공 MP4를 보존하고 별도 GIF로 변환합니다. Instagram은 위의 오디오 트랙별 저장 규칙을 따릅니다.
- 메인과 팝업은 최근 결과 한 묶음을 공유합니다. X에는 서랍을 표시하지 않습니다. 전체 요청 주소는 메모리에만 보관하므로 프로세스 재시작 뒤 저장에는 재조회가 필요할 수 있습니다.

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

검증 환경: Windows, JDK 21 실행기, Android SDK Platform 36.1 및 Build Tools 36.1.0. Gradle 데몬은 `gradle/gradle-daemon-jvm.properties`에 지정된 JDK 25를 사용합니다. 실행기 버전과 데몬 버전을 구분하며 초기 도구 다운로드에는 네트워크가 필요합니다.
Gradle 9.5.1/Android Gradle Plugin 9.2.1은 프로젝트에 고정돼 있습니다.
`app`은 통합 앱, `xmedia`는 공유 X 코드 라이브러리, `xprobe`는 독립 진단 앱입니다.

```powershell
$env:JAVA_HOME = '<JDK 21 설치 경로>'
$env:ANDROID_HOME = '<Android SDK 설치 경로>'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :xmedia:testDebugUnitTest :app:lintDebug
```

산출물: `app/build/outputs/apk/debug/app-debug.apk`. 릴리스 서명 연결 코드는 포함하지만 개인 키와 비밀번호는 포함하지 않습니다. 아래 외부 서명 설정을 따르세요.
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
# Release signing

Before publishing, follow [source and APK release checks](third_party/RELEASE_CHECKS.md).
Run `python tools/check_public_tree.py` on the final publication branch. Acquired
upstream sources and notices do not yet close the Android native source gate.

`app` release builds read UTF-8 Java properties from
`~/keyStore/signing.properties`, or the file specified by
`BROWSERDOWNLOADER_SIGNING_PROPERTIES`. Required fields: `storeFile`,
`storePassword`, `keyAlias`, `keyPassword`. Use forward slashes in paths;
relative keystore paths resolve from the properties file's directory.
Keep this file and the keystore outside the repository.

Build with `./gradlew :app:assembleRelease --no-configuration-cache` (Windows:
`./gradlew.bat`). Output: `app/build/outputs/apk/release/app-release.apk`.
Missing signing configuration fails release signing; debug builds retain their
development signature. A release signature does not guarantee Play Protect approval.
