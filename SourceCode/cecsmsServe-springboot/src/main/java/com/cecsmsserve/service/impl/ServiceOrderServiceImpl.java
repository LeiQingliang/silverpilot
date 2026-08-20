package com.cecsmsserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cecsmsserve.entity.ServiceOrder;
import com.cecsmsserve.mapper.ServiceOrderMapper;
import com.cecsmsserve.service.IServiceOrderService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cecsmsserve.util.result.CommonResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author GoatCode
 * @since 2024-07-17
 */
@Service
public class ServiceOrderServiceImpl extends ServiceImpl<ServiceOrderMapper, ServiceOrder> implements IServiceOrderService {

    private final ServiceOrderMapper mapper;

    public ServiceOrderServiceImpl(ServiceOrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CommonResult<List<ServiceOrder>> selectAll() {
        List<ServiceOrder> list=mapper.selectList(null);
        CommonResult<List<ServiceOrder>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<ServiceOrder>> selectAllByPage(int current, int size) {
        current = normalizeCurrent(current);
        size = normalizeSize(size);
        int start=(current-1)*size;
        List<ServiceOrder> list=mapper.selectAllByPage(start,size);
        long total=mapper.selectCount(null);
        CommonResult<List<ServiceOrder>> result=new CommonResult<>(String.valueOf(total),list);
        return result;
    }

    @Override
    public CommonResult<List<ServiceOrder>> selectServiceByPage(int current, int size) {
        current = normalizeCurrent(current);
        size = normalizeSize(size);
        int start=(current-1)*size;
        List<ServiceOrder> list=mapper.selectServiceByPage(start,size);
        LambdaQueryWrapper<ServiceOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(ServiceOrder::getTypeBId, 4);
        long total=mapper.selectCount(wrapper);
        CommonResult<List<ServiceOrder>> result=new CommonResult<>(String.valueOf(total),list);
        return result;
    }

    @Override
    public CommonResult<List<ServiceOrder>> selectHealthByPage(int current, int size) {
        current = normalizeCurrent(current);
        size = normalizeSize(size);
        int start=(current-1)*size;
        List<ServiceOrder> list=mapper.selectHealthByPage(start,size);
        LambdaQueryWrapper<ServiceOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ServiceOrder::getTypeBId, 4);
        long total=mapper.selectCount(wrapper);
        CommonResult<List<ServiceOrder>> result=new CommonResult<>(String.valueOf(total),list);
        return result;
    }

    @Override
    public CommonResult<List<ServiceOrder>> selectByUId(int uId) {
        List<ServiceOrder> list=mapper.selectByUId(uId);
        CommonResult<List<ServiceOrder>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<ServiceOrder>> selectByuIdByState(int uId, int state) {
        List<ServiceOrder> list=mapper.selectByuIdByState(uId,state);
        CommonResult<List<ServiceOrder>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<ServiceOrder>> selectByState(int orderState) {
        List<ServiceOrder> list=mapper.selectByState(orderState);
        CommonResult<List<ServiceOrder>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<ServiceOrder> insert(ServiceOrder serviceOrder) {
        if (serviceOrder == null || serviceOrder.getuId() == null
                || serviceOrder.getTypeBId() == null || serviceOrder.getTypeSId() == null
                || serviceOrder.getReserveDate() == null
                || serviceOrder.getServiceAddress() == null || serviceOrder.getServiceAddress().isBlank()) {
            return CommonResult.validateFailed("服务预约信息不完整");
        }
        serviceOrder.setId(null);
        serviceOrder.setOrderState(0);
        serviceOrder.setOrderDate(java.time.LocalDate.now());
        serviceOrder.setOrderTime(java.time.LocalTime.now().withNano(0));
        serviceOrder.setmId(null);
        serviceOrder.setdId(null);
        serviceOrder.setAcceptDate(null);
        serviceOrder.setAcceptTime(null);
        serviceOrder.setFinishDate(null);
        serviceOrder.setFinishTime(null);
        serviceOrder.setRate(null);
        serviceOrder.setName(null);
        serviceOrder.setTelephone(null);
        int i=mapper.insert(serviceOrder);
        CommonResult<ServiceOrder> result=new CommonResult<>(serviceOrder);
        if(i<=0){
            result.setNotInserted();
        }
        return result;
    }

    @Override
    @Transactional
    public CommonResult<Integer> transition(ServiceOrder serviceOrder, int expectedState) {
        if (serviceOrder == null || serviceOrder.getId() == null || serviceOrder.getOrderState() == null) {
            return CommonResult.validateFailed("订单状态变更信息不完整");
        }
        int targetState = serviceOrder.getOrderState();
        boolean allowed = (expectedState == 0 && (targetState == 1 || targetState == 2))
                || (expectedState == 2 && targetState == 3)
                || (expectedState == 3 && targetState == 4);
        if (!allowed) {
            return CommonResult.validateFailed("订单状态流转无效");
        }
        LambdaUpdateWrapper<ServiceOrder> condition = new LambdaUpdateWrapper<ServiceOrder>()
                .eq(ServiceOrder::getId, serviceOrder.getId())
                .eq(ServiceOrder::getOrderState, expectedState);
        int updated = mapper.update(serviceOrder, condition);
        return updated == 1
                ? CommonResult.success(updated, "订单状态已更新")
                : CommonResult.validateFailed("订单状态已变更，请刷新后重试");
    }

    @Override
    @Transactional
    public CommonResult<Void> cancelByUser(Integer orderId, Integer userId) {
        if (orderId == null || orderId <= 0 || userId == null) {
            return CommonResult.validateFailed("订单或用户编号无效");
        }
        ServiceOrder order = mapper.selectById(orderId);
        if (order == null) {
            return CommonResult.notFound("服务订单不存在");
        }
        if (!userId.equals(order.getuId())) {
            return CommonResult.forbidden("无权取消此服务订单");
        }
        if (!Integer.valueOf(0).equals(order.getOrderState())) {
            return CommonResult.validateFailed("只有待受理订单可以取消");
        }
        return mapper.cancelPendingByUser(orderId, userId) == 1
                ? CommonResult.success(null, "取消服务预约成功")
                : CommonResult.validateFailed("订单状态已变更，请刷新后重试");
    }

    @Override
    public CommonResult<List<HashMap<String,Object>>> countRate() {
        List<HashMap<String,Object>> list=mapper.countRate();
        return CommonResult.success(list == null ? List.of() : list);
    }

    private int normalizeCurrent(int current) {
        return Math.max(current, 1);
    }

    private int normalizeSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }
}
