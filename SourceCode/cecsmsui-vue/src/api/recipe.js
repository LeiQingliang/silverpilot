import request from '@/utils/axios'

// 菜谱管理接口
export function getRecipeList(params) {
  return request({
    url: '/recipe/list',
    method: 'get',
    params
  })
}

export function getRecipeDetail(id) {
  return request({
    url: `/recipe/${id}`,
    method: 'get'
  })
}

export function saveRecipe(data) {
  return request({
    url: '/recipe',
    method: 'post',
    data
  })
}

export function updateRecipe(data) {
  return request({
    url: '/recipe',
    method: 'put',
    data
  })
}

export function deleteRecipe(id) {
  return request({
    url: `/recipe/${id}`,
    method: 'delete'
  })
}

// 预订管理接口
export function getAllOrders(params) {
  return request({
    url: '/recipe-order/all',
    method: 'get',
    params
  })
}

// 更新订单状态
export function updateOrderStatus(data) {
  return request({
    url: '/recipe-order/status',
    method: 'put',
    data
  })
}
