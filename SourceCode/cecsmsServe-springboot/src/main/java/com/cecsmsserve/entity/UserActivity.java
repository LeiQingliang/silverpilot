package com.cecsmsserve.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * <p>
 *
 * </p>
 *
 * @author GoatCode
 * @since 2024-07-22
 */
@TableName("user_activity")
public class UserActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 用户id
     */
    @JsonProperty("uId")
    private Integer uId;

    /**
     * 活动id
     */
    @JsonProperty("aId")
    private Integer aId;

    /**
     * 报名日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd",timezone = "GMT+8")
    private LocalDate enterDate;

    /**
     * 报名时间
     */
    @JsonFormat(pattern = "HH:mm:ss",timezone = "GMT+8")
    private LocalTime enterTime;

    private String state;

    @TableField(exist = false)
    private User user;

    @TableField(exist = false)
    private Activity activity;

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
    public Integer getaId() {
        return aId;
    }

    public void setaId(Integer aId) {
        this.aId = aId;
    }
    public LocalDate getEnterDate() {
        return enterDate;
    }

    public void setEnterDate(LocalDate enterDate) {
        this.enterDate = enterDate;
    }
    public LocalTime getEnterTime() {
        return enterTime;
    }

    public void setEnterTime(LocalTime enterTime) {
        this.enterTime = enterTime;
    }
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Activity getActivity() {
        return activity;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    @Override
    public String toString() {
        return "UserActivity{" +
                "id=" + id +
                ", uId=" + uId +
                ", aId=" + aId +
                ", enterDate=" + enterDate +
                ", enterTime=" + enterTime +
                ", state=" + state +
                ", user=" + user +
                ", activity=" + activity +
                '}';
    }
}
