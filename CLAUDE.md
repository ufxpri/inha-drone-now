# CLAUDE.md — 작업 인수인계 / 프로젝트 컨텍스트

이 파일은 **다음 AI(Claude Code) 세션이 작업을 이어갈 수 있도록** 프로젝트 상태·결정·함정을 정리한 문서입니다.
(사용자: 조승준, 12253182 / 인하공전 객체지향프로그래밍 2 기말 프로젝트)

---

## 1. 프로젝트 한 줄 요약
Java 21 Swing 데스크톱 앱. 지도 클릭 위치 기준으로 기상·일출일몰·NOTAM·공역(비행금지구역) 정보를
종합해 **GO/CAUTION/NO-GO** 비행 가부를 판정한다. 설계는 `기말자료/12주차_클래스설계보고서` 기반.

## 2. 빌드 / 실행 (중요)
Maven이 PATH에 없음. **VS Code Pleiades 팩 번들 Maven**을 사용한다:
```
MVN="C:\Users\ufxpri\AppData\Roaming\Code\User\globalStorage\pleiades.java-extension-pack-jdk\maven\latest\bin\mvn.cmd"
cd C:\Users\ufxpri\inha-drone-now
"$MVN" -B compile        # 빌드
"$MVN" -B exec:java      # 실행 (com.drone.App)
```
- Java 21 (record/switch 사용). 번들 Maven은 3.9.16.
- 실행 확인은 화면을 볼 수 없으므로 `tasklist | grep java.exe` + stderr 로그(`/tmp/app.log`)로 한다.
- 컴파일 검증은 클린 빌드 권장: `rm -rf target/classes` 후 compile.

## 3. ⚠️ 알려진 함정
- **(과거 이슈) 구글 드라이브 동기화가 `.class` 파일을 손상**시켜 `Truncated class file` /
  `NoClassDefFoundError`를 유발했었음. → 그래서 프로젝트를 `C:\Users\ufxpri\inha-drone-now`
  (드라이브 밖)로 옮김. **여기서 작업할 것.** 원본은 `G:\...\12253182_조승준-기말_과제`에 남아있음.
- **config.properties**: 실제 API 키가 들어있고 `.gitignore`로 git 제외됨. 절대 커밋 금지.
  새 환경이면 `.example` 보고 키 채워야 4개 API 동작.
- **Bash 도구로 `python3 -c "여러 줄"`** 실행 시 들여쓰기 깨짐(셸 래핑 이슈). 파이썬은
  `cat > /tmp/x.py << 'EOF'` 로 파일 작성 후 실행. 또 git-bash `/tmp`는 실제로
  `C:\Users\ufxpri\AppData\Local\Temp` 이며 Windows python에서 경로 다르게 봄.

## 4. 외부 데이터 연동 핵심 지식 (디버깅으로 알아낸 것)
- **기상청 초단기실황**: `apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst`.
  위경도→격자 변환 필요. base_time은 매시 40분 발표라 보수적으로 직전 시각 사용.
- **천문연 출몰시각**: `apis.data.go.kr/B090041/.../getLCRiseSetInfo`. XML 응답. 공공데이터포털
  키 1개를 기상청과 공용.
- **NOTAM**: 공개 OpenAPI 없음. 항공정보포털 조회 엔드포인트 사용:
  - 세션: `GET https://aim.koca.go.kr/xNotam/index.do?type=search&language=ko_KR` → JSESSIONID 획득
  - 검색: `POST https://aim.koca.go.kr/xNotam/searchAllNotam.do?ibpage=1` (form 데이터, JSON 응답)
  - Q-line `(\d{4})([NS])(\d{5})([EW])(\d{3})`로 중심좌표+반경(NM) 파싱 → 지도에 원. 반경 999=FIR전역(미표시).
- **V-World 공역**: 라이브 데이터 API는 함정이 많아 **GeoJSON 번들 방식으로 전환**함.
  - (참고) 라이브 데이터 API: 레이어명에 `_INFO` 접미사 필요할 때 있음(LT_C_ADSIDO_INFO),
    geomFilter의 POINT는 **공백 구분**(`POINT(lon lat)`), BOX는 콤마.
  - (참고) WFS 수집 시: typename **소문자**, version=1.1.0, srsname=EPSG:3857, 회당 1000건 제한.
  - **현재 구현**: `src/main/resources/geojson/`에 9종 GeoJSON 번들(357 폴리곤). `NoFlyZoneStore`가
    로드. 좌표는 **EPSG:3857 → WGS84 변환** 필요(아래 공식). 점-포함은 ray-casting.
    ```
    lon = x / 20037508.34 * 180
    lat = toDegrees(2*atan(exp(toRadians(y/20037508.34*180))) - PI/2)
    ```

## 5. 아키텍처 (패키지)
```
com.drone
├── App, ConfigLoader
├── model          Location, Spot, PilotLicense, GeoUtil
│   └── dto        WeatherInfo, NotamInfo(+NotamItem), NoFlyZoneInfo(+Zone), SunTimeInfo
├── api            ApiClient<T> ← CachedApiClient<T> ← Weather/Notam/VWorld/SunTimeClient
├── io             FileCacheStore(TTL JSON 캐시), AppDataStore(자격증·즐겨찾기), NoFlyZoneStore(GeoJSON)
├── judge          FlightSafetyJudge, FlightSafetyReport(+ChecklistItem+Verdict)
├── controller     DashboardController(클릭→4데이터→판정→뷰), SpotController
└── view           MainFrame + MapPanel(JxMapViewer) + ChecklistPanel + Weather/Notam/NoFlyZone/SunTime/SpotList
```
- 판정: 풍속/강수/NOTAM/공역교차/주간/자격 6항목 → 하나라도 NO_GO면 NO_GO, CAUTION 있으면 CAUTION.
- 공역 9종 분류(NoFlyZoneStore.Category): 금지/제한/관제권/위험/임시=비행금지(NO_GO),
  경계/장애물/사전협의/드론전용=정보성.

## 6. UI 현황
VS Code 스타일 단일 화면(탭 없음):
- 상단: 자격 콤보 + GO/CAUTION/NO-GO 배너
- 좌측: 활동바(📍관심지점/🔔NOTAM 토글) + 사이드바
- 중앙: 지도(Carto Light 타일) — 공역 폴리곤 + NOTAM 원 + 클릭 마커
- 우측: 체크리스트(상태 점·컬러)
- 하단: 기상/일출일몰/비행금지구역/NOTAM 4열 (게이지·나침반·타임라인 등 Java2D 시각화)
- 즐겨찾기: 지도 클릭 위치를 이름만 입력해 저장. 저장 항목 더블클릭 시 재조회.

## 7. 진행 상태
- ✅ 전체 구현 완료, 4개 데이터 소스 실연동 검증, 9종 공역 통합, README 작성, GitHub 푸시
- ⬜ #12 통합 테스트·예외처리 보강 (남음)
- ⬜ #13 15주차 시연 영상 제작 (남음)
- git: `main` 브랜치, 원격 `git@github.com:ufxpri/inha-drone-now.git`

## 8. 다음 세션이 하면 좋을 일
- 다양한 위치/시간대(야간·악천후·금지구역 내외)로 판정 정확성 통합 테스트
- 네트워크 오류/타임아웃/키 누락 시 사용자 메시지 정비
- (선택) 장애물공역 151개 폴리곤 렌더링 성능 — 뷰포트 밖 폴리곤 컬링 검토
- 시연 시나리오 정리 (청와대=비행금지, 김포=관제권, 한적한 곳=GO 등 데모 포인트)
