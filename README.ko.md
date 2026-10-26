# OmniMorph

[简体中文](README.md) · [日本語](README.ja.md) · [English](README.en.md) · **한국어**

OmniMorph는 [Figura](https://github.com/FiguraMC/Figura)를 기반으로 하는 Minecraft Java 클라이언트 모드입니다. Figura Avatar와 YSM 모델을 공통 옷장, 미리보기, 조작 화면에서 사용할 수 있습니다.

Lua와 Molang 런타임을 함께 지원합니다. Lua는 스크립트와 상호작용을, Molang은 모델 표현식과 애니메이션 제어를 담당합니다. YSM 모델은 별도의 Lua 스크립트를 작성하지 않아도 불러와 실행할 수 있습니다.

[빌드 다운로드](https://github.com/AIH-Interactive/omni-morph/actions/workflows/fabric-build.yml) · [변경 기록](docs/CHANGELOG.ko.md) · [문제 제보](https://github.com/AIH-Interactive/omni-morph/issues) · [연동 백엔드](https://github.com/AIH-Interactive/sculptor-for-omni-morph)

## 기능

- **통합 모델 로딩**: Figura 모델 폴더와 ZIP, 지원되는 형식의 YSM 폴더, ZIP 및 암호화된 `.ysm` 파일.
- **모델 조작**: 옷장 필터, 모델 미리보기, 텍스처 전환, 모델 전용 설정, 액션 휠.
- **애니메이션과 스크립트**: YSM 애니메이션 컨트롤러, Molang 표현식, Figura Lua API와 YSM 인터페이스 연동.
- **모델 라이브러리**: 업로드, 분류, 검색, 즐겨찾기, 다운로드, 장착, 기본 정보 편집.
- **접근 제어와 관리**: 공개·비공개 모델, 사용 권한 관리, 신고, 이의 신청, 모델 차단, 휴지통 복원.
- **멀티플레이 공유**: 호환 백엔드를 통한 모델 배포와 동적 상태 동기화. Figura Cloud 및 Protobuf 지원.
- **선택적 하드웨어 가속**: SIMD, GPU, 자동 모드를 제공하며 기본적으로 꺼져 있습니다. 사용할 수 없으면 Java 처리로 전환합니다.
- **인터페이스 언어**: 중국어 간체와 영어를 기본 포함합니다.

## 실행 환경

| 항목 | 버전 |
| --- | --- |
| Minecraft Java | 26.1.2 |
| 모드 로더 | Fabric |
| Java | 25 |
| 모드 버전 | 1.0.0-alpha.5 |

빌드 의존성 버전은 [gradle.properties](gradle.properties)를 참고하세요. 클라이언트용 모드이며, 멀티플레이 리소스 배포와 상태 동기화에는 호환 백엔드가 필요합니다.

## 설치 및 사용

1. Minecraft 26.1.2용 Fabric을 설치하고 Java 25로 게임을 실행합니다.
2. 성공한 워크플로 실행에서 최종 모드 JAR을 다운로드하여 해당 게임 인스턴스의 `mods` 폴더에 넣습니다.
3. 게임을 실행하고 OmniMorph 옷장을 엽니다. 모델 폴더에 모델을 넣은 뒤 선택하여 불러옵니다.
4. 모델 전용 설정과 액션 휠로 모델을 조작합니다. 모델 라이브러리에서 클라우드 모델을 업로드하거나 관리하고 다운로드할 수 있습니다.

기본 모델 폴더는 `.minecraft/omnimorph/avatars`입니다. 설정에서 OmniMorph 폴더 위치를 변경할 수 있습니다. 옷장의 폴더 버튼으로 실제 사용 중인 모델 디렉터리를 열 수 있습니다.

설정은 `omni-morph.json`에 저장됩니다. 최초 이전 시 기존 `figura.json`을 읽고 원래 설정 파일을 유지할 수 있습니다.

모델 형식과 버전에 따라 지원 기능이 다릅니다. 모델 스크립트, 필요한 리소스, 권한 및 백엔드 정책도 사용 가능한 기능에 영향을 줍니다.

## Lua와 Molang

OmniMorph는 Figura의 Lua 환경을 유지하며 YSM 모델, 애니메이션, 액션에 접근할 수 있는 `ysm_model`, `ysm_actions` 인터페이스를 제공합니다. `ysm_model:isYsm()`으로 현재 모델 형식을 확인할 수 있습니다. 모델 부품, 텍스처 및 애니메이션을 Lua 스크립트와 연동할 수 있습니다.

YSM 모델의 표현식과 애니메이션 컨트롤러는 Molang 런타임에서 처리합니다. 모델이 제공하는 컨트롤, 액션 및 언어 리소스는 해당 화면에서 사용됩니다. 사용자 지정 모델 이름과 내용은 모델 제작자가 정의합니다.

## 백엔드 연결

설정에서 서버 주소를 입력하고 해당 서버가 지원하는 프로토콜을 선택하세요.

- **Protobuf**: 기본 프로토콜이며 OmniMorph Protobuf를 지원하는 백엔드가 필요합니다.
- **Figura Cloud**: 기존 Figura Cloud 서비스와의 호환을 위한 프로토콜입니다.

프로토콜이 호환되지 않아도 클라이언트가 자동으로 다른 프로토콜로 전환하지 않습니다. 서버 주소는 기본적으로 HTTPS/WSS를 사용하며, 명시적인 HTTP/WS는 디버그 모드에서만 허용됩니다. 연결에 실패해도 로컬 모델은 불러올 수 있습니다.

연동 백엔드로 [sculptor-for-omni-morph](https://github.com/AIH-Interactive/sculptor-for-omni-morph)를 권장합니다. 백엔드 설정과 배포 방법은 해당 프로젝트의 [배포 문서](https://github.com/AIH-Interactive/sculptor-for-omni-morph/blob/master/docs/DEPLOYMENT.md)를 참고하세요.

## 소스에서 빌드

JDK 25를 준비하고 `JAVA_HOME`, `PATH`를 설정한 뒤 저장소 루트에서 실행합니다.

```powershell
& .\gradlew.bat :fabric:build --console=plain 2>&1 | Tee-Object -FilePath 'build.log'
$buildExitCode = $LASTEXITCODE
if ($buildExitCode -ne 0) {
    Get-Content -LiteralPath 'build.log' -Tail 40
    exit $buildExitCode
}
```

최종 결과물은 `fabric/build/libs/`에 생성되며 파일 이름은 `omni-morph-...-fabric-mc.jar`입니다.

일반 Fabric 빌드에는 C++ 툴체인이 필요하지 않습니다. 로컬 SIMD 라이브러리 빌드는 [native/README.md](native/README.md), 프로토콜 사양은 [protocol/README.md](protocol/README.md)를 참고하세요.

## 감사의 말

다음 프로젝트와 기여자 여러분께 감사드립니다.

- [Figura](https://github.com/FiguraMC/Figura): Avatar, Lua, 권한 및 인터페이스의 기반 코드.
- [YesSteveModel](https://github.com/YesSteveModel/YesSteveModel)과 [YesSteveModel-Native](https://github.com/YesSteveModel/YesSteveModel-Native): YSM 모델 생태계 및 구현 참고.
- [Sparkle-Morpher](https://github.com/sdf123098/Sparkle-Morpher), [OpenYSM / YSMParser](https://github.com/OpenYSM/YSMParser): 모델 실행 동작 및 형식 분석 참고.
- [openysm.cpp](https://github.com/OpenYSMDev/openysm.cpp): SIMD/native 알고리즘 비교 참고.
- [Sculptor](https://github.com/MrXiaoM/sculptor)와 [spm-cloud](https://github.com/sdf123098/spm-cloud): 백엔드 및 프로토콜 설계 참고.
- [FiguraMC/Assets](https://github.com/FiguraMC/Assets): 일부 중국어 간체 인터페이스 번역.

기여자와 외부 출처 정보는 [CREDITS](CREDITS) 및 함께 제공되는 라이선스·저작자 표시 파일을 참고하세요. 상위 프로젝트 소개는 [Figura 원본 README](docs/README.Figura.original.md)에 보존되어 있습니다.

## AI 개발 보조

OmniMorph는 코드 구현, 문제 분석, 테스트 및 문서 정리에 AI 도구를 사용합니다. 프로젝트 방향, 작업 범위에 대한 판단 및 릴리스는 유지 관리자가 책임집니다. 기능의 완성 여부는 실제 코드, 빌드 결과, 테스트 및 인수 확인 기록을 바탕으로 판단합니다.

모드 아이콘을 포함한 일부 미술 리소스는 OpenAI GPT Image로 생성한 후 편집했습니다.

## 라이선스

이 프로젝트는 [PolyForm Noncommercial License 1.0.0](LICENSE.md)을 사용합니다. 외부 코드, 의존성 및 리소스에는 각각 함께 제공되는 라이선스가 적용됩니다.
