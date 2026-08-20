package com.cecsmsserve.util.exception;

//自定义异常类
public class CustomException extends RuntimeException{
    private String msg;

    public CustomException(String msg){
        this.msg=msg;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
