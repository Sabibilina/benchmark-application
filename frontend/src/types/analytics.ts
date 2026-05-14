export interface HistoryEvent {
  eventId: string;
  type: string;
  userId: string;
  songId: string;
  timestamp: string;
}

export interface HistoryPage {
  content: HistoryEvent[];
  totalElements: number;
  page: number;
  size: number;
}

export interface GlobalChartItem {
  songId: string;
  playCount: number;
  rank: number;
}
