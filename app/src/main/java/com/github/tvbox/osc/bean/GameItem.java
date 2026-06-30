package com.github.tvbox.osc.bean;

public class GameItem {
    public String name;
    public String url;
    public String type; // "h5" for HTML5 games

    public GameItem() {}

    public GameItem(String name, String url, String type) {
        this.name = name;
        this.url = url;
        this.type = type;
    }
}
