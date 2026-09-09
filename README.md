<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_art.png" width="144" height="144" alt="돋보기를 든 모아미 캐릭터">
</p>

<h1 align="center">MOAMI - 모아미</h1>

<p align="center">
  <strong>브라우저와 SNS의 미디어를 모아 보고, 골라 저장하세요.</strong><br>
  Android 10+ · ARM64 · Material 3 · <a href="LICENSE">GPL-3.0-only</a>
</p>

<p align="center">
  <a href="#features">주요 기능</a> · <a href="#usage">사용 방법</a> ·
  <a href="#build">직접 빌드</a> · <a href="#limits">지원 범위</a> ·
  <a href="#license">라이선스</a>
</p>

모아미는 Chrome에서 찾은 이미지와 X·Instagram의 공개 미디어를 갤러리에서 선택 저장하는 Android 앱입니다. 이미지를 하나씩 새 탭으로 열고 저장하는 반복 작업을 줄이기 위해 만들고 있습니다.

> **개발 중 · APK 미공개**
> 현재는 소스 코드 공개를 준비하고 있습니다. 내려받을 수 있는 공식 APK는 제공하지 않습니다. 직접 빌드는 아래 안내를 참고하세요.

<a id="features"></a>
## 주요 기능

| 가져오는 곳 | 시작 방법 | 저장 대상 |
| --- | --- | --- |
| Chrome 현재 탭 | 가장자리 핸들 → 현재 탭 검사 | Chrome이 전달한 이미지 주소로 수집한 이미지 |
| 웹페이지 주소 | 메인 주소창에 링크 입력 | 공개 HTML에서 찾은 이미지 |
| X 공개 게시물 | 게시물 공유 또는 링크 입력 | 사진·MP4 영상·GIF |
| Instagram 게시물·릴스 | 게시물 공유 또는 링크 입력 | 사진·영상, 오디오 트랙이 없는 영상의 GIF 변환 |

- **갤러리에서 선택 저장** — 썸네일을 비교하고 확대해서 확인한 뒤 필요한 항목을 선택합니다.
- **저장 결과 확인** — 완료된 셀의 체크 배지·테두리, 완료·미완료 집계와 메시지로 결과를 확인합니다.
- **Chrome 가장자리 핸들** — 표시를 켜고 끌 수 있으며, 길게 누른 뒤 위아래로 끌어 위치를 바꿉니다. X에는 핸들을 표시하지 않습니다.
- **화면 크기에 맞는 UI** — Material 3 갤러리와 결과 팝업, 시스템 밝기 모드와 지원 기기의 동적 색상을 사용합니다.

<a id="usage"></a>
## 사용 방법

### 처음 실행하기

이용·데이터 처리 안내를 읽고 동의한 뒤, 튜토리얼을 따라 접근성 서비스와 실행 알림을 허용하세요. 필요한 설정이 꺼져 있으면 설정 안내가 다시 표시됩니다.

접근성이 제한된 경우 Android 13 이상에서는 앱 정보의 **제한된 설정 허용** 안내를 제공합니다. 실제 허용은 사용자가 시스템 설정에서 수행합니다. 설치 전 Play Protect 차단을 해제하는 기능은 아닙니다.

### 미디어 가져오기

**Chrome에서 보고 있는 이미지**

1. 모아미에서 서랍 표시를 켜고 Chrome으로 이동합니다.
2. 가장자리의 `<` 핸들 → **현재 탭 검사**를 누릅니다.
3. 결과 팝업에서 이미지를 확인합니다. 더 가져오려면 직접 스크롤한 뒤 다시 검사하세요.

같은 문서의 재검사는 결과를 누적합니다. 자동으로 스크롤하지 않으며, 아직 로딩되지 않았거나 Chrome이 정보를 제공하지 않는 이미지는 빠질 수 있습니다.

**X·Instagram 게시물**

게시물의 **공유 → MOAMI - 모아미**를 선택하면 미디어를 조회하고 팝업 그리드에 표시합니다. 메인 주소창에 게시물 링크를 입력해도 됩니다.

**일반 웹페이지 링크**

메인 주소창에 링크를 넣고 확인하면 공개 HTML에서 이미지를 찾습니다. Chrome 현재 탭 검사와 별개이며, 로그인 상태나 브라우저에서 실행된 JavaScript 결과를 가져오지 않습니다.

### 선택하고 저장하기

갤러리에서 항목을 선택하고 **선택 저장**을 누르세요. 저장 위치는 `Download/BrowserDownloader`입니다.

저장이 끝나면 셀의 **저장됨** 표시와 완료·미완료 개수를 확인하세요. 이는 현재 검사 세션의 결과이며, 기기의 파일을 계속 추적하는 다운로드 이력은 아닙니다.

<a id="limits"></a>
## 지원 범위와 알아둘 점

**미리보기 표시는 원본 확인을 뜻하지 않습니다.** 사이트가 제공하는 주소와 해상도 후보를 사용하며, 업로드 원본을 일괄 보장하지 않습니다.

| 항목 | 현재 동작과 제한 |
| --- | --- |
| 지원 기기 | Android 10(API 29) 이상, ARM64(`arm64-v8a`)만 지원. x86_64 에뮬레이터 제외 |
| Chrome 검사 | 이미지 주소 500개·카드 1,000개 보호 한도가 있습니다. |
| 웹주소 수집 | 로그인·JavaScript 실행·CSS 배경·iframe 내부 수집은 지원하지 않습니다. |
| X GIF | 제공된 MP4를 보존하고 별도 GIF로 변환합니다. |
| Instagram 영상 | 실제 파일에 오디오 트랙이 있으면 MP4, 없으면 GIF만 저장합니다. 무음 오디오 트랙도 ‘있음’으로 처리합니다. |
| GIF 변환 | 10fps·256색으로 색상 손실이 있습니다. 30초·1080p 픽셀 수 이하가 시험 범위입니다. Instagram GIF 실패 시 MP4로 대체 저장하지 않습니다. |
| 공개 SNS 콘텐츠 | 사용자 쿠키·API 키를 사용하지 않습니다. 로그인 요구·요청 제한·사이트 변경으로 조회가 실패할 수 있습니다. |
| 최근 결과 | 메인과 팝업이 최근 결과 한 묶음을 공유합니다. 전체 요청 주소는 메모리에만 보관하므로 프로세스 재시작 후 재조회가 필요할 수 있습니다. |

저장 권한이 있는 콘텐츠만 이용하세요. 앱의 소스 라이선스가 콘텐츠의 다운로드·재배포 권한을 제공하지는 않습니다.

<a id="build"></a>
## 직접 빌드하기

저장소를 내려받고 루트 폴더에서 실행합니다. 아래는 Windows에서 확인한 환경입니다.

| 구성 | 버전 |
| --- | --- |
| Gradle 실행기 / 데몬 | JDK 21 / JDK 25 — [데몬 설정](gradle/gradle-daemon-jvm.properties)으로 고정 |
| Android SDK / Build Tools | Platform 36.1 / 36.1.0 |
| Gradle / Android Gradle Plugin | 9.5.1 / 9.2.1 — 프로젝트에 고정 |

초기 도구와 의존성 다운로드에는 네트워크가 필요합니다.

```powershell
$env:JAVA_HOME = '<JDK 21 설치 경로>'
$env:ANDROID_HOME = '<Android SDK 설치 경로>'
.\gradlew.bat :app:assembleDebug
```

산출물: `app/build/outputs/apk/debug/app-debug.apk`

<details>
<summary><strong>개인 인증서로 릴리스 빌드</strong></summary>

서명키와 설정 파일은 저장소 밖에 보관하세요. 기본 설정 위치는 `~/keyStore/signing.properties`이며, `BROWSERDOWNLOADER_SIGNING_PROPERTIES` 환경변수로 다른 경로를 지정할 수 있습니다.

UTF-8 Java properties 형식으로 `storeFile`, `storePassword`, `keyAlias`, `keyPassword`가 필요합니다. 경로에는 `/`를 사용하며, 상대 키 경로는 설정 파일이 있는 폴더를 기준으로 합니다.

```powershell
.\gradlew.bat :app:assembleRelease --no-configuration-cache
```

산출물: `app/build/outputs/apk/release/app-release.apk`

서명 설정이 없으면 릴리스 서명이 실패합니다. 서명 성공은 Play Protect 또는 Google Play 승인을 뜻하지 않습니다. APK를 배포하기 전 [배포 확인 항목](third_party/RELEASE_CHECKS.md)을 확인하세요.

</details>

<details>
<summary><strong>검사 명령과 프로젝트 구성</strong></summary>

```powershell
.\gradlew.bat :app:testDebugUnitTest :xmedia:testDebugUnitTest :app:lintDebug
python -m unittest discover -s tools -p 'test_*.py'
python tools/check_public_tree.py
```

Python 검사 도구에는 별도의 Python 실행 환경이 필요합니다. 공개 정보 검사는 패턴 기반 검사이며 모든 개인정보 부재를 보증하지 않습니다.

| 경로 | 역할 |
| --- | --- |
| `app/` | 모아미 통합 Android 앱 |
| `xmedia/` | X·Instagram 미디어 기능을 통합 앱에 제공하는 라이브러리 |
| `xprobe/` | 미디어 기능의 공통 소스와 독립 진단 앱 |
| `tools/` | 호스트·실기기 전달·공개 정보 검사 도구 |
| `third_party/` | 의존성 소스·네이티브 빌드 근거·배포 확인 문서 |

실기기 계측은 일반 빌드와 별개입니다. 계측 후에는 [최종 APK 업데이트 검사](tools/verify_apk_update.py)로 접근성 서비스 연결까지 확인합니다.

네이티브 런타임 재빌드는 [WSL2 빌드 절차](third_party/WSL_NATIVE_BUILD.md)를 따릅니다. 일반 APK 빌드에 Docker는 필요하지 않습니다.

</details>

## 개발 상태와 참여

현재 앱 버전은 `0.33.0-scalable-icon`입니다. 작은 설정 화면 아이콘에서도 캐릭터가 표시되도록 크기에 비례한 여백을 적용했습니다. Chrome 사이트 정보 창의 닫기 버튼이 없는 경우를 처리하는 수정은 포함됐으며, 해당 문제 기기의 수정 후 실사용 확인은 대기 중입니다. 모든 지원 기기·사이트의 검증이 끝난 상태는 아닙니다.

ARM64 Python·QuickJS의 소스 빌드는 완료했지만, 새 런타임의 APK 통합과 실기기 회귀 검증은 남아 있습니다. APK 공개 전 필요한 대응 소스·고지 검토는 [네이티브 소스 확인 현황](third_party/NATIVE_SOURCE_GAPS.md)에 정리합니다.

문제를 제보할 때는 앱 버전, Android 버전, 재현 순서, 기대한 결과와 실제 결과를 함께 알려주세요. 로그·스크린샷에서 개인 콘텐츠와 계정 정보, 쿠키·토큰·인증 URL을 제거하세요. 개발 작업의 기록 절차는 [AGENTS.md](AGENTS.md)를 참고하세요.

<a id="license"></a>
## 라이선스

Copyright (C) 2026 BrowserDownloader contributors.

자체 소스 코드·문서·직접 제작 시험 리소스는 **GNU GPL version 3 only (`GPL-3.0-only`)**로 제공합니다. 상품성·특정 목적 적합성을 포함한 보증을 제공하지 않으며, 수정·재배포 조건은 [LICENSE](LICENSE)를 따릅니다.

제3자 구성물에는 각자의 라이선스가 적용됩니다. GPL-3.0인 youtubedl-android 0.18.1을 비롯한 의존성과 내장 리소스의 출처는 [제3자 고지](xprobe/THIRD_PARTY_NOTICES.md)에 보존합니다.

APK를 배포하려면 앱 소스 외에도 필요한 의존성의 대응 소스와 빌드 입력·고지를 갖춰야 합니다. 현재 소스 공개 준비와 APK 배포 준비는 별개이며, 빌드 출력·APK·개인 서명키는 Git에서 제외합니다.
