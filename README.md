# BrowserDownloader

Android에서 Chrome 이미지와 공개 X 게시물 미디어를 선택 저장하는 개발 중 앱입니다.
UI/UX 변경 전 기준 버전은 `0.21.0-service-lifecycle`입니다.

- Chrome: 현재 탭 검사 → 결과 팝업 → 선택 저장. 직접 스크롤한 뒤 재검사하면 같은 문서의 결과를 누적합니다.
- X: 게시물 공유 → BrowserDownloader G1 → 미디어 그리드 → 선택 저장. 직접 링크 입력도 지원합니다.
- 저장 폴더: `Download/BrowserDownloader`. 사용할 권한이 있는 미디어만 저장하세요.
- 사이트별 원본 확인 범위가 다릅니다. GIF는 제공 MP4를 보존하고 별도 GIF로 변환합니다.

## 라이선스·배포 상태

**현재 통합 앱은 MIT-only 배포 대상이 아닙니다.** `:app → :xmedia`와 독립 `:xprobe`는
GPL-3.0인 youtubedl-android 0.18.1을 사용하며 NativeProbe에서 직접 호출합니다.
자체 코드의 라이선스는 소유자 결정 대기이며 이 README는 라이선스 부여가 아닙니다.
GPL 구성물의 조건을 MIT로 대체하지 않습니다.

포함 Python/QuickJS/전이 구성물의 고지 및 대응 소스 검토도 미완료입니다.
공개 APK/Release 업로드 준비 완료를 의미하지 않습니다.
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
