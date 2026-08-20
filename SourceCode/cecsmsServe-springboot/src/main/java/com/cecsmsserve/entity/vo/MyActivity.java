package com.cecsmsserve.entity.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalTime;

public class MyActivity {
    private Integer id;

    @JsonProperty("uId")
    private Integer uId;
    private String myState;
    private String activityName;
    private LocalDate activityDate;

    private LocalTime startTime;

    private String activityAddress;
    private Integer activityPoint;

    private String name;
    private String telephone;
    @JsonProperty("aState")
    private String aState;

    private String type;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getuId() {
        return uId;
    }

    public void setuId(Integer uId) {
        this.uId = uId;
    }

    public String getMyState() {
        return myState;
    }

    public void setMyState(String myState) {
        this.myState = myState;
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public void setActivityDate(LocalDate activityDate) {
        this.activityDate = activityDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public String getActivityAddress() {
        return activityAddress;
    }

    public void setActivityAddress(String activityAddress) {
        this.activityAddress = activityAddress;
    }

    public Integer getActivityPoint() {
        return activityPoint;
    }

    public void setActivityPoint(Integer activityPoint) {
        this.activityPoint = activityPoint;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getaState() {
        return aState;
    }

    public void setaState(String aState) {
        this.aState = aState;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "MyActivity{" +
                "id=" + id +
                ", uId=" + uId +
                ", myState='" + myState + '\'' +
                ", activityName='" + activityName + '\'' +
                ", activityDate=" + activityDate +
                ", startTime=" + startTime +
                ", activityAddress='" + activityAddress + '\'' +
                ", activityPoint=" + activityPoint +
                ", name='" + name + '\'' +
                ", telephone='" + telephone + '\'' +
                ", aState='" + aState + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
