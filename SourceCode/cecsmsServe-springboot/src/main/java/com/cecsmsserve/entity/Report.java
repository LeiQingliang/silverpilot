package com.cecsmsserve.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author GoatCode
 * @since 2024-08-20
 */
public class Report implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 用户编号
     */
    @JsonProperty("uId")
    private Integer uId;

    /**
     * 医护人员编号
     */
    @JsonProperty("dId")
    private Integer dId;

    /**
     * 体检日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime time;

    /**
     * 身高
     */
    private Double height;

    /**
     * 体重
     */
    private Double weight;

    /**
     * 血压
     */
    private String bp;

    /**
     * 白细胞
     */
    private String wbc;

    /**
     * 红细胞
     */
    private String rbc;

    /**
     * 血小板
     */
    private String plt;

    /**
     * 血红蛋白
     */
    private String hgb;

    /**
     * 淋巴细胞
     */
    private String lym;

    /**
     * 中性粒细胞比率
     */
    private String neut;

    /**
     * 总胆固醇（血脂）
     */
    private String tcho;

    /**
     * 甘油三酯（血脂）
     */
    private String tg;

    /**
     * 高密度脂蛋白胆固醇（血脂）
     */
    private String hdlc;

    /**
     * 低密度脂蛋白胆固醇（血脂）
     */
    private String ldlc;

    /**
     * 谷丙转氨酶（肝功）
     */
    private String alt;

    /**
     * 谷草转氨酶（肝功）
     */
    private String ast;

    /**
     * 血清胆红素（肝功）
     */
    private String sb;

    /**
     * 血糖
     */
    private String bs;

    /**
     * 尿葡萄糖（尿常规 阴/阳 mmol/L）
     */
    private String glu;

    /**
     * 尿胆红素（尿常规 阴/阳 umol/L）
     */
    private String bil;

    /**
     * 尿酮体（尿常规 阴/阳 mg/L）
     */
    private String ket;

    /**
     * 尿胆原（尿常规 阴/阳）
     */
    private String uro;

    /**
     * 尿酸碱度（尿常规 5-7）
     */
    private String ph;

    /**
     * 尿比重 （尿常规 ）
     */
    private String sg;

    /**
     * 血尿酸（肾功 男性210~420μmol/L ；女性150~350μmol/L ）
     */
    private String ua;

    /**
     * 血肌酐（肾功 男62~115μmol/L ；女53~ 97μmol/L。）
     */
    private String scr;

    /**
     * 尿素氮（肾功1.75-8.05mmol/L）
     */
    private String bun;

    /**
     * 甲胎蛋白：原发性肝细胞癌的重要标志物 ng/ml <7.0
     */
    private String afp;

    /**
     * 癌胚抗原 :肿瘤标志物 μg/L <5.0
     */
    private String cea;

    /**
     * 铁蛋白 :诊断缺铁性贫血、肝病等，也是恶性肿瘤的标志物之一。 男性在15-200ug/L，女性在12-150µg/L。
     */
    private String ferritin;

    @TableField(exist = false)
    private User doctor;

    @TableField(exist = false)
    private User user;

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

    public Integer getdId() {
        return dId;
    }

    public void setdId(Integer dId) {
        this.dId = dId;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public Double getHeight() {
        return height;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getBp() {
        return bp;
    }

    public void setBp(String bp) {
        this.bp = bp;
    }

    public String getWbc() {
        return wbc;
    }

    public void setWbc(String wbc) {
        this.wbc = wbc;
    }

    public String getRbc() {
        return rbc;
    }

    public void setRbc(String rbc) {
        this.rbc = rbc;
    }

    public String getPlt() {
        return plt;
    }

    public void setPlt(String plt) {
        this.plt = plt;
    }

    public String getHgb() {
        return hgb;
    }

    public void setHgb(String hgb) {
        this.hgb = hgb;
    }

    public String getLym() {
        return lym;
    }

    public void setLym(String lym) {
        this.lym = lym;
    }

    public String getNeut() {
        return neut;
    }

    public void setNeut(String neut) {
        this.neut = neut;
    }

    public String getTcho() {
        return tcho;
    }

    public void setTcho(String tcho) {
        this.tcho = tcho;
    }

    public String getTg() {
        return tg;
    }

    public void setTg(String tg) {
        this.tg = tg;
    }

    public String getHdlc() {
        return hdlc;
    }

    public void setHdlc(String hdlc) {
        this.hdlc = hdlc;
    }

    public String getLdlc() {
        return ldlc;
    }

    public void setLdlc(String ldlc) {
        this.ldlc = ldlc;
    }

    public String getAlt() {
        return alt;
    }

    public void setAlt(String alt) {
        this.alt = alt;
    }

    public String getAst() {
        return ast;
    }

    public void setAst(String ast) {
        this.ast = ast;
    }

    public String getSb() {
        return sb;
    }

    public void setSb(String sb) {
        this.sb = sb;
    }

    public String getBs() {
        return bs;
    }

    public void setBs(String bs) {
        this.bs = bs;
    }

    public String getGlu() {
        return glu;
    }

    public void setGlu(String glu) {
        this.glu = glu;
    }

    public String getBil() {
        return bil;
    }

    public void setBil(String bil) {
        this.bil = bil;
    }

    public String getKet() {
        return ket;
    }

    public void setKet(String ket) {
        this.ket = ket;
    }

    public String getUro() {
        return uro;
    }

    public void setUro(String uro) {
        this.uro = uro;
    }

    public String getPh() {
        return ph;
    }

    public void setPh(String ph) {
        this.ph = ph;
    }

    public String getSg() {
        return sg;
    }

    public void setSg(String sg) {
        this.sg = sg;
    }

    public String getUa() {
        return ua;
    }

    public void setUa(String ua) {
        this.ua = ua;
    }

    public String getScr() {
        return scr;
    }

    public void setScr(String scr) {
        this.scr = scr;
    }

    public String getBun() {
        return bun;
    }

    public void setBun(String bun) {
        this.bun = bun;
    }

    public String getAfp() {
        return afp;
    }

    public void setAfp(String afp) {
        this.afp = afp;
    }

    public String getCea() {
        return cea;
    }

    public void setCea(String cea) {
        this.cea = cea;
    }

    public String getFerritin() {
        return ferritin;
    }

    public void setFerritin(String ferritin) {
        this.ferritin = ferritin;
    }

    public User getDoctor() {
        return doctor;
    }

    public void setDoctor(User doctor) {
        this.doctor = doctor;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public String toString() {
        return "Report{" +
                "id=" + id +
                ", uId=" + uId +
                ", dId=" + dId +
                ", time=" + time +
                ", height=" + height +
                ", weight=" + weight +
                ", bp='" + bp + '\'' +
                ", wbc='" + wbc + '\'' +
                ", rbc='" + rbc + '\'' +
                ", plt='" + plt + '\'' +
                ", hgb='" + hgb + '\'' +
                ", lym='" + lym + '\'' +
                ", neut='" + neut + '\'' +
                ", tcho='" + tcho + '\'' +
                ", tg='" + tg + '\'' +
                ", hdlc='" + hdlc + '\'' +
                ", ldlc='" + ldlc + '\'' +
                ", alt='" + alt + '\'' +
                ", ast='" + ast + '\'' +
                ", sb='" + sb + '\'' +
                ", bs='" + bs + '\'' +
                ", glu='" + glu + '\'' +
                ", bil='" + bil + '\'' +
                ", ket='" + ket + '\'' +
                ", uro='" + uro + '\'' +
                ", ph='" + ph + '\'' +
                ", sg='" + sg + '\'' +
                ", ua='" + ua + '\'' +
                ", scr='" + scr + '\'' +
                ", bun='" + bun + '\'' +
                ", afp='" + afp + '\'' +
                ", cea='" + cea + '\'' +
                ", ferritin='" + ferritin + '\'' +
                ", doctor=" + doctor +
                ", user=" + user +
                '}';
    }
}
