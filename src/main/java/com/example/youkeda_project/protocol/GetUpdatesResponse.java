package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class GetUpdatesResponse {

    private int ret;

    private List<WeixinMessage> msgs;

    @JsonProperty("get_updates_buf")
    private String getUpdatesBuf;

    public int getRet() {
        return ret;
    }

    public void setRet(int ret) {
        this.ret = ret;
    }

    public List<WeixinMessage> getMsgs() {
        return msgs;
    }

    public void setMsgs(List<WeixinMessage> msgs) {
        this.msgs = msgs;
    }

    public String getGetUpdatesBuf() {
        return getUpdatesBuf;
    }

    public void setGetUpdatesBuf(String getUpdatesBuf) {
        this.getUpdatesBuf = getUpdatesBuf;
    }
}