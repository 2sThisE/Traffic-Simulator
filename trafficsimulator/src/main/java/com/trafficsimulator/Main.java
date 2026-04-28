package com.trafficsimulator;

import com.trafficsimulator.debug.DebugUI;
import com.trafficsimulator.ui.RoadVisualizer;

public class Main {
    public static void main(String[] args) {
        if(args[0].equals("-d"))
            DebugUI.main(args); // 디버그 UI 실행
        else RoadVisualizer.main(args);
    }
}