# Navigate 클래스 메서드 가이드

`com.trafficsimulator.util.Navigate` 클래스는 교통 시뮬레이션 내에서 도로 네트워크 분석, 경로 탐색 및 데이터 변환을 담당하는 핵심 유틸리티 클래스입니다.

## 1. 네트워크 분석 (Network Analysis)

### `findGlobalEndpoints(roadManager, junctionController)`
*   **역할**: 전체 도로망에서 차량이 생성될 수 있는 '시작 차선'과 사라지는 '종점 차선'들의 세트를 찾습니다.
*   **로직**:
    *   모든 도로의 모든 차선을 전수 조사합니다.
    *   해당 차선으로 들어오는 연결(`LaneConnection`)이 없으면 `startLanes`에 추가합니다.
    *   `isTrueDeadEnd` 로직을 통해 더 이상 갈 곳이 없는 차선을 `endLanes`에 추가합니다.

### `isTrueDeadEnd(lane, roadManager, junctionController)`
*   **역할**: 특정 차선이 정말로 네트워크의 끝인지 엄격하게 판단합니다.
*   **로직**: 단순히 현재 차선의 연결만 보는 것이 아니라, 해당 도로에서 같은 주행 방향을 가진 **모든 차선**에 나가는 연결이 하나도 없을 때만 `true`를 반환합니다.

---

## 2. 경로 탐색 (Pathfinding)

### `calculateRoute(startLane, roadManager, junctionController)`
*   **역할**: 시작 지점부터 임의의 종점까지 가는 **최적의 단일 경로**를 계산합니다.
*   **반환값**: `List<Set<Lane>>` (도로 구간별 가용 차선 그룹의 리스트)
*   **특징**: 단순히 차선 하나를 찾는 것이 아니라, "다음 도로로 넘어가기 위해 미리 타두어야 하는 차선들"을 계산하여 구간(Phase)별로 주행 가능한 차선 집합을 제공합니다.

### `calculateAllRoutes(startLane, roadManager, junctionController)`
*   **역할**: 가능한 **여러 개의 후보 경로**를 최대 10개까지 탐색하여 반환합니다.
*   **반환값**: `List<List<Set<Lane>>>` (여러 경로들의 리스트)

### `findSingleLanePathWithLaneChanges` & `findAllLanePathsWithLaneChanges`
*   **역할**: BFS(너비 우선 탐색) 알고리즘을 사용하여 실제 차선 단위의 물리적 연결 경로를 찾습니다.
*   **특징**: 차선 간의 직접적인 연결뿐만 아니라, 같은 도로 내에서의 **차선 변경(Lane Change)** 가능성까지 고려하여 경로를 확장합니다.

---

## 3. 데이터 변환 및 시각화 (Data Conversion)

### `generateTotalPathPoints(routePhases, junctionController)`
*   **역할**: 추상적인 `List<Set<Lane>>` 경로 데이터를 화면에 그릴 수 있는 연속적인 좌표 리스트(`List<Point2D.Double>`)로 변환합니다.
*   **로직**:
    *   각 구간(Phase)에서 다음 구간으로 연결되는 가장 적절한 '대표 차선'을 하나 선택합니다.
    *   선택된 차선의 내부 점들과 교차로의 연결선(`LaneConnection`) 점들을 순서대로 병합하여 하나의 긴 곡선 데이터를 만듭니다.
