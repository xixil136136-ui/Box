package com.github.tvbox.osc.bean;

import java.util.List;

public class RadioGroup {
    public String group;
    public List<RadioChannel> channels;

    public RadioGroup() {}

    public RadioGroup(String group, List<RadioChannel> channels) {
        this.group = group;
        this.channels = channels;
    }
}
