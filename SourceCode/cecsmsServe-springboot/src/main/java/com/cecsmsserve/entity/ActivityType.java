package com.cecsmsserve.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * <p>
 *
 * </p>
 *
 * @author GoatCode
 * @since 2024-07-17
 */
@TableName("activity_type")
public class ActivityType implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 活动类型
     */
    private String type;

    /**
     * 活动类型描述
     */
    private String detail;

    /**
     * 活动目的
     */
    private String purpose;

    /**
     * 活动状态
     */
    private Integer state;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
    public Integer getState() {
        return state;
    }

    public void setState(Integer state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "ActivityType{" +
            "id=" + id +
            ", type=" + type +
            ", detail=" + detail +
            ", purpose=" + purpose +
            ", state=" + state +
        "}";
    }
}
