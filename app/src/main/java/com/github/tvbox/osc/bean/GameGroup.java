package com.github.tvbox.osc.bean;

import java.util.List;

public class GameGroup {
    public String group;
    public List<GameItem> games;

    public GameGroup() {}

    public GameGroup(String group, List<GameItem> games) {
        this.group = group;
        this.games = games;
    }
}
