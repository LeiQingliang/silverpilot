package com.cecsmsserve.entity.vo;


public class PageVo {
    //当前页数
    protected int currentPage;
    //每页行数
    protected int pageSize;
    //每页的起始行
    protected int start;

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getStart() {
        return (this.currentPage-1)*this.pageSize;
    }

    public void setStart(int start) {
        this.start = start;
    }

}
