package com.cecsmsserve.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.cecsmsserve.entity.vo.SignedUserList;
import com.cecsmsserve.service.IUserActivityService;
import com.cecsmsserve.util.JWTInterceptor;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/download")
public class DownloadExcelController {

    private final IUserActivityService userActivityService;

    public DownloadExcelController(IUserActivityService userActivityService) {
        this.userActivityService = userActivityService;
    }

    @PostMapping("/excel/{aId}")
    public void export(
            @PathVariable int aId,
            HttpServletResponse response,
            HttpServletRequest request) throws IOException {
        Object roleValue = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        if (!(roleValue instanceof Integer roleId) || (roleId != 1 && roleId != 2)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "没有导出报名名单的权限");
            return;
        }
        if (aId <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "活动编号无效");
            return;
        }

        List<SignedUserList> userList = userActivityService.selectUserList(aId);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=activity-" + aId + "-registrations.xlsx");

        WriteCellStyle headStyle = new WriteCellStyle();
        WriteFont headFont = new WriteFont();
        headFont.setFontHeightInPoints((short) 11);
        headFont.setBold(false);
        headStyle.setWriteFont(headFont);

        WriteCellStyle contentStyle = new WriteCellStyle();
        WriteFont contentFont = new WriteFont();
        contentFont.setFontHeightInPoints((short) 11);
        contentStyle.setWriteFont(contentFont);
        contentStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);

        HorizontalCellStyleStrategy styleStrategy =
                new HorizontalCellStyleStrategy(headStyle, contentStyle);
        try (ServletOutputStream output = response.getOutputStream()) {
            EasyExcel.write(output, SignedUserList.class)
                    .sheet("报名名单")
                    .registerWriteHandler(styleStrategy)
                    .doWrite(userList);
        }
    }
}
