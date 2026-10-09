import { get, post } from '@/api/request'

export interface Supplier {
  supplierId: number
  legalName: string
  licenseNo: string
  paymentTermDays: number
  status: string
}

export interface PurchaseOrder {
  poId: number
  poNo: string
  supplierId: number
  totalCent: number
  status: string
  createdAt: string
}

export function createSupplier(data: { legalName: string; licenseNo: string; contactEnc: string; bankAccountEnc: string; paymentTermDays?: number }) {
  return post<number>('/admin/suppliers', null, { params: data })
}

export function updateSupplierStatus(id: number, status: string) {
  return post(`/admin/suppliers/${id}/status`, null, { params: { status } })
}

export function createPurchaseOrder(data: { supplierId: number; buyerEntityId: number; currency?: string; items: any[] }) {
  return post<string>('/admin/purchase-orders', data.items, { params: { supplierId: data.supplierId, buyerEntityId: data.buyerEntityId, currency: data.currency } })
}

export function approvePurchaseOrder(id: number, approverId: number, paymentTermDays?: number) {
  return post(`/admin/purchase-orders/${id}/approve`, null, { params: { approverId, paymentTermDays } })
}

export function getAvailableStock(warehouseId: number, skuId: number, batchNo: string) {
  return get<number>('/admin/stock/available', { warehouseId, skuId, batchNo })
}

export function blockStock(data: { warehouseId: number; skuId: number; batchNo: string; qty: number; businessKey: string }) {
  return post('/admin/stock/block', null, { params: data })
}

export function createShipment(data: { orderId: number; carrier: string; items: any[] }) {
  return post<number>('/admin/shipments', data.items, { params: { orderId: data.orderId, carrier: data.carrier } })
}
