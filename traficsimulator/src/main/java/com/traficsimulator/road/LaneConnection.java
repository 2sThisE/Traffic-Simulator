package com.traficsimulator.road;

import java.awt.geom.Point2D;
import java.util.List;

/**
 * 차선 연결 정보와 교차로 내 통행 경로를 담는 레코드입니다.
 */
public record LaneConnection(Lane targetLane, LaneType laneType, List<Point2D.Double> connectionPath) {}
