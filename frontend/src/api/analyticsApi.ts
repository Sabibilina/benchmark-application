import type { GlobalChartItem, HistoryPage } from "../types/analytics";
import { api } from "./client";

export async function fetchHistory(page = 0, size = 50): Promise<HistoryPage> {
  const response = await api.analytics.get<HistoryPage>("/analytics/me/history", { params: { page, size } });
  return response.data;
}

export async function fetchGlobalCharts(): Promise<GlobalChartItem[]> {
  const response = await api.analytics.get<GlobalChartItem[]>("/analytics/charts/global");
  return response.data;
}
