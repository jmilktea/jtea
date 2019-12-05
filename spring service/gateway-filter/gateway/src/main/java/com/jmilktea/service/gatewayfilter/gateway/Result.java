package com.jmilktea.service.gatewayfilter.gateway;

/**
 * @author huangyb
 * @date 2019/12/5
 */
public class Result {

    public Result(Boolean success, String msg) {
        this.success = success;
        this.msg = msg;
    }

    private Boolean success;

    private String msg;

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
