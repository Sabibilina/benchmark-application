import { notificationClient } from './client'
import type { Notification } from '../types'

export const notificationApi = {
  getAll() {
    return notificationClient.get<Notification[]>('/notifications')
  },
}
