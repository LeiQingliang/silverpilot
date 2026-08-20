package com.cecsmsserve.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * <p>
 *
 * </p>
 *
 * @author GoatCode
 * @since 2024-07-17
 */
@TableName("service_order")
public class ServiceOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 用户编号
     */
    @JsonProperty("uId")
    private Integer uId;

    /**
     * 社区工作者编号
     */
    @JsonProperty("mId")
    private Integer mId;

    /**
     * 医护人员编号
     */
    @JsonProperty("dId")
    private Integer dId;

    /**
     * 服务大类
     */
    private Integer typeBId;

    /**
     * 服务小类
     */
    private Integer typeSId;

    /**
     * 预约日期
     */
    private LocalDate reserveDate;

    /**
     * 服务地点
     */
    private String serviceAddress;

    /**
     * 订单详情
     */
    private String orderDetail;

    /**
     * 下单日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd",timezone = "GMT+8")
    private LocalDate orderDate;

    /**
     * 下单时间
     */
    @JsonFormat(pattern = "HH:mm:ss",timezone = "GMT+8")
    private LocalTime orderTime;

    /**
     * 受理日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd",timezone = "GMT+8")
    private LocalDate acceptDate;

    /**
     * 受理时间
     */
    @JsonFormat(pattern = "HH:mm:ss",timezone = "GMT+8")
    private LocalTime acceptTime;

    /**
     * 完成日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd",timezone = "GMT+8")
    private LocalDate finishDate;

    /**
     * 完成时间
     */
    @JsonFormat(pattern = "HH:mm:ss",timezone = "GMT+8")
    private LocalTime finishTime;

    /**
     * 订单评分
     */
    private Double rate;

    /**
     * 订单状态
     */
    private Integer orderState;

    private String name;

    private String telephone;

    //uId
    @TableField(exist = false)
    private User user;

    //mId
    @TableField(exist = false)
    private User manager;

    //typeId
    @TableField(exist = false)
    private ServiceType typeB;

    @TableField(exist = false)
    private ServiceType typeS;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDate getReserveDate() {
        return reserveDate;
    }

    public void setReserveDate(LocalDate reserveDate) {
        this.reserveDate = reserveDate;
    }

    public String getServiceAddress() {
        return serviceAddress;
    }

    public void setServiceAddress(String serviceAddress) {
        this.serviceAddress = serviceAddress;
    }

    public void setOrderDetail(String orderDetail) {
        this.orderDetail = orderDetail;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public LocalTime getOrderTime() {
        return orderTime;
    }

    public LocalDate getAcceptDate() {
        return acceptDate;
    }

    public LocalTime getAcceptTime() {
        return acceptTime;
    }

    public LocalDate getFinishDate() {
        return finishDate;
    }

    public LocalTime getFinishTime() {
        return finishTime;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public void setOrderTime(LocalTime orderTime) {
        this.orderTime = orderTime;
    }

    public void setAcceptDate(LocalDate acceptDate) {
        this.acceptDate = acceptDate;
    }

    public void setAcceptTime(LocalTime acceptTime) {
        this.acceptTime = acceptTime;
    }

    public void setFinishDate(LocalDate finishDate) {
        this.finishDate = finishDate;
    }

    public void setFinishTime(LocalTime finishTime) {
        this.finishTime = finishTime;
    }

    public Integer getuId() {
        return uId;
    }

    public void setuId(Integer uId) {
        this.uId = uId;
    }
    public Integer getmId() {
        return mId;
    }

    public void setmId(Integer mId) {
        this.mId = mId;
    }
    public Integer getdId() {
        return dId;
    }

    public void setdId(Integer dId) {
        this.dId = dId;
    }

    public Integer getTypeBId() {
        return typeBId;
    }

    public void setTypeBId(Integer typeBId) {
        this.typeBId = typeBId;
    }

    public Integer getTypeSId() {
        return typeSId;
    }

    public void setTypeSId(Integer typeSId) {
        this.typeSId = typeSId;
    }

    public String getOrderDetail() {
        return orderDetail;
    }

    public Double getRate() {
        return rate;
    }

    public void setRate(Double rate) {
        this.rate = rate;
    }

    public Integer getOrderState() {
        return orderState;
    }

    public void setOrderState(Integer orderState) {
        this.orderState = orderState;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getManager() {
        return manager;
    }

    public void setManager(User manager) {
        this.manager = manager;
    }

    public ServiceType getTypeB() {
        return typeB;
    }

    public void setTypeB(ServiceType typeB) {
        this.typeB = typeB;
    }

    public ServiceType getTypeS() {
        return typeS;
    }

    public void setTypeS(ServiceType typeS) {
        this.typeS = typeS;
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

    @Override
    public String toString() {
        return "ServiceOrder{" +
                "id=" + id +
                ", uId=" + uId +
                ", mId=" + mId +
                ", dId=" + dId +
                ", typeBId=" + typeBId +
                ", typeSId=" + typeSId +
                ", reserveDate=" + reserveDate +
                ", serviceAddress='" + serviceAddress + '\'' +
                ", orderDetail='" + orderDetail + '\'' +
                ", orderDate=" + orderDate +
                ", orderTime=" + orderTime +
                ", acceptDate=" + acceptDate +
                ", acceptTime=" + acceptTime +
                ", finishDate=" + finishDate +
                ", finishTime=" + finishTime +
                ", rate=" + rate +
                ", orderState=" + orderState +
                ", name='" + name + '\'' +
                ", telephone='" + telephone + '\'' +
                ", user=" + user +
                ", manager=" + manager +
                ", typeB=" + typeB +
                ", typeS=" + typeS +
                '}';
    }
}
